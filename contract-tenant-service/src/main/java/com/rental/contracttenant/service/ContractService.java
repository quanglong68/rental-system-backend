package com.rental.contracttenant.service;

import com.rental.common.error.BusinessException;
import com.rental.contracttenant.dto.ContractResponse;
import com.rental.contracttenant.dto.CreateDraftContractRequest;
import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.repository.ContractRepository;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository contracts;
    private final PlatformTransactionManager transactionManager;

    /**
     * Creates one DRAFT per accepted rental request; replay returns the existing row.
     * Concurrency handling:
     * When concurrent duplicate requests race, both may observe absence. One succeeds in committing
     * the INSERT, while the other fails with a unique constraint violation (DataIntegrityViolationException).
     * Handling the insert inside an isolated transaction allows catching the constraint violation safely
     * without continuing in a rollback-only transaction. On unique violation, a fresh read-only transaction
     * reloads the committed row and executes deterministic replay validation.
     */
    public ContractResponse createDraft(CreateDraftContractRequest request) {
        if (request == null) {
            throw new BusinessException("VALIDATION_ERROR", "Request khong duoc rong", HttpStatus.BAD_REQUEST);
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);

        try {
            return template.execute(status -> {
                return contracts.findByRentalRequestId(request.getRentalRequestId())
                        .map(existing -> {
                            validateReplay(existing, request);
                            return ContractResponse.from(existing);
                        })
                        .orElseGet(() -> ContractResponse.from(contracts.save(toDraft(request))));
            });
        } catch (DataIntegrityViolationException dive) {
            // Concurrent race: another transaction committed between our find and save.
            // In a fresh read-only transaction, reload and validate replay deterministically.
            TransactionTemplate readOnlyTemplate = new TransactionTemplate(transactionManager);
            readOnlyTemplate.setReadOnly(true);

            Contract existing = readOnlyTemplate.execute(status ->
                    contracts.findByRentalRequestId(request.getRentalRequestId())
                            .orElse(null)
            );

            if (existing != null) {
                validateReplay(existing, request);
                return ContractResponse.from(existing);
            }

            // If not found, failure was unrelated data integrity violation (not rental_request_id unique conflict)
            throw dive;
        }
    }

    private void validateReplay(Contract existing, CreateDraftContractRequest request) {
        if (!Objects.equals(existing.getRoomId(), request.getRoomId())
                || !Objects.equals(existing.getBuildingId(), request.getBuildingId())
                || !Objects.equals(existing.getCustomerId(), request.getCustomerId())
                || !Objects.equals(existing.getStartDate(), request.getMoveInDate())
                || existing.getRentAmount() == null
                || request.getPrice() == null
                || existing.getRentAmount().compareTo(normalizeMoney(request.getPrice())) != 0) {
            throw new BusinessException("RENTAL_REQUEST_CONFLICT", "Rental request da gan hop dong khac du lieu",
                    HttpStatus.CONFLICT);
        }
    }

    private Contract toDraft(CreateDraftContractRequest request) {
        Contract contract = new Contract();
        contract.setRentalRequestId(request.getRentalRequestId());
        contract.setRoomId(request.getRoomId());
        contract.setBuildingId(request.getBuildingId());
        contract.setCustomerId(request.getCustomerId());
        contract.setStatus(ContractStatus.DRAFT);
        contract.setStartDate(request.getMoveInDate());
        contract.setRentAmount(normalizeMoney(request.getPrice()));
        return contract;
    }

    private static BigDecimal normalizeMoney(BigDecimal amount) {
        return amount.stripTrailingZeros().scale() < 0 ? amount.setScale(0) : amount;
    }
}
