package com.rental.contracttenant.service;

import com.rental.common.error.BusinessException;
import com.rental.common.error.NotFoundException;
import com.rental.contracttenant.dto.ContractResponse;
import com.rental.contracttenant.dto.CreateDraftContractRequest;
import com.rental.contracttenant.dto.UpdateDraftContractRequest;
import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.entity.ContractTenant;
import com.rental.contracttenant.outbox.OutboxEventPublisher;
import com.rental.contracttenant.repository.ContractRepository;
import com.rental.contracttenant.repository.ContractTenantRepository;
import java.math.BigDecimal;
import java.util.List;
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
    private final ContractTenantRepository tenants;
    private final OutboxEventPublisher outbox;
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

    /**
     * QUAN_LY repairs the negotiable terms of one DRAFT contract (spec 9.4 "Chốt giá thuê").
     *
     * <p>Immutable identity fields (rentalRequestId, roomId, buildingId, customerId) cannot be
     * silently overwritten: they are absent from {@link UpdateDraftContractRequest} and this method
     * writes only the four negotiable fields onto the loaded entity. Status is likewise never
     * written here — the endpoint cannot set it.
     *
     * <p>The read is a {@code PESSIMISTIC_WRITE} row lock ({@link ContractRepository#findByIdForUpdate}),
     * the same read {@link #sign} uses, so an update and a signing of the same contract cannot
     * interleave: whichever transaction gets the lock second sees the other's committed status.
     *
     * <p>LIMITATION (unchanged from Task 3, restated for signing): this row lock serializes only
     * operations on <em>this</em> contract. It says nothing about two different contracts of the same
     * room or customer — cross-contract serialization is the advisory lock taken in {@link #sign}.
     */
    public ContractResponse updateDraftDetails(Long id, UpdateDraftContractRequest request) {
        if (request == null) {
            throw new BusinessException("VALIDATION_ERROR", "Request khong duoc rong", HttpStatus.BAD_REQUEST);
        }
        // Re-checked here, not only by @NotNull on the DTO: a service-level caller (tests, a future
        // internal caller) bypasses bean validation, and a null startDate must fail loudly rather
        // than NPE inside the comparison below.
        if (request.getStartDate() == null) {
            throw new BusinessException("VALIDATION_ERROR", "Ngay bat dau khong duoc rong", HttpStatus.BAD_REQUEST);
        }
        // A rental term must have positive length, so equal dates are rejected too; endDate stays
        // optional because a DRAFT may not have its term agreed yet.
        if (request.getEndDate() != null && !request.getEndDate().isAfter(request.getStartDate())) {
            throw new BusinessException("INVALID_DATE", "Ngay ket thuc phai sau ngay bat dau",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);

        return template.execute(status -> contracts.findByIdForUpdate(id)
                .map(existing -> {
                    if (existing.getStatus() != ContractStatus.DRAFT) {
                        throw new BusinessException("INVALID_STATUS", "Chi sua hop dong o trang thai DRAFT",
                                HttpStatus.UNPROCESSABLE_ENTITY);
                    }
                    applyDetails(existing, request);
                    return ContractResponse.from(contracts.save(existing));
                })
                .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong " + id)));
    }

    private static void applyDetails(Contract contract, UpdateDraftContractRequest request) {
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setRentAmount(normalizeMoney(request.getRentAmount()));
        contract.setDepositAmount(request.getDepositAmount() == null ? null
                : normalizeMoney(request.getDepositAmount()));
    }

    /**
     * QUAN_LY signs one DRAFT contract (spec 9.4 "Ký hợp đồng"): status becomes ACTIVE, exactly one
     * initial {@code contract_tenant} is created for the original customer as representative, and a
     * {@code ContractSigned} outbox row is written in the same transaction.
     *
     * <p><b>Lock protocol — this exact order is mandatory for every operation that checks both the
     * customer and the room constraint:</b>
     * <ol>
     *   <li>advisory lock {@code "customer:<customerId>"};</li>
     *   <li>advisory lock {@code "room:<roomId>"};</li>
     *   <li>{@code PESSIMISTIC_WRITE} on this contract row ({@link ContractRepository#findByIdForUpdate}),
     *       the same read the draft update uses.</li>
     * </ol>
     * Customer before room, and only then the row. Any two paths that take both advisory locks in
     * opposite orders can deadlock inside {@code pg_advisory_xact_lock}; any path that takes only the
     * row lock cannot see another contract at all. This implementation ({@link #sign}) is the
     * reference order — copy it verbatim rather than reordering to match local reading order.
     *
     * <p>The two advisory locks are what serialize two signings of <em>different</em> contracts:
     * the row lock alone only serializes operations on <em>this</em> row.
     *
     * <p>LIMITATION: they do not make the room-overlap and customer-membership rules hold for contract
     * writes this service does not own. The future {@code POST /contracts/{id}/tenants} (adding a
     * tenant) must take all three locks in the order above — customer, then room, then the contract
     * row — otherwise a tenant added concurrently with a signing of another contract of the same room
     * can still slip through.
     *
     * <p>Retry safety: a second sign of the same contract finds it non-DRAFT and is rejected with
     * {@code 422 INVALID_STATUS}, so no duplicate tenant row and no duplicate logical event; the
     * V2 partial unique indexes stay as a backstop.
     */
    public ContractResponse sign(Long id) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        return template.execute(status -> {
            Contract contract = contracts.findById(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong " + id));

            // Cheap checks first, on the row already read, so a doomed request takes no lock.
            // Status is checked BEFORE completeness: a contract that is both non-DRAFT and incomplete
            // must report INVALID_STATUS ("this contract cannot be signed at all") rather than
            // CONTRACT_INCOMPLETE ("fix the term and retry"), which would invite a retry that can
            // never succeed. The reload below orders them the same way.
            if (contract.getStatus() != ContractStatus.DRAFT) {
                throw new BusinessException("INVALID_STATUS", "Chi ky hop dong o trang thai DRAFT",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            validateSigningFields(contract);

            // Fixed lock order: customer key before room key, matching the documented protocol.
            contracts.lockNamedKey(signKey("customer", contract.getCustomerId()));
            contracts.lockNamedKey(signKey("room", contract.getRoomId()));

            Contract locked = contracts.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong " + id));
            // Re-checked after taking the lock: the status may have changed while we waited for it.
            if (locked.getStatus() != ContractStatus.DRAFT) {
                throw new BusinessException("INVALID_STATUS", "Chi ky hop dong o trang thai DRAFT",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            validateSigningFields(locked);

            if (contracts.existsActiveOverlappingContract(locked.getRoomId(), ContractStatus.ACTIVE,
                    locked.getStartDate(), locked.getEndDate(), locked.getId())) {
                throw new BusinessException("ROOM_ALREADY_CONTRACTED",
                        "Phong da co hop dong ACTIVE trung khoang thoi gian", HttpStatus.CONFLICT);
            }
            if (tenants.existsCurrentTenantInContractWithStatus(locked.getCustomerId(), ContractStatus.ACTIVE)) {
                // The spec names no code for this on the sign row; this reuses the documented
                // tenant-rule code (spec 9.4 "một khách không được thuộc hai hợp đồng ACTIVE cùng lúc").
                throw new BusinessException("CUSTOMER_ALREADY_IN_ROOM",
                        "Khach da thuoc mot hop dong ACTIVE khac", HttpStatus.CONFLICT);
            }

            locked.setStatus(ContractStatus.ACTIVE);
            Contract saved = contracts.save(locked);

            try {
                tenants.save(initialRepresentative(saved));
            } catch (DataIntegrityViolationException race) {
                // Backstop: another signing won between our checks and this insert. V2's partial
                // unique index is what fires; the caller must not see a 500 for a business rule.
                throw new BusinessException("CUSTOMER_ALREADY_IN_ROOM",
                        "Khach da thuoc mot hop dong ACTIVE khac", HttpStatus.CONFLICT);
            }

            // Same transaction as the status change and the tenant row (spec 5.3); never a direct
            // Kafka send from here.
            outbox.publishContractSigned(saved, List.of(saved.getCustomerId()));

            return ContractResponse.from(saved);
        });
    }

    /**
     * Mandatory signing fields (spec 9.4 + {@code 422 CONTRACT_INCOMPLETE}). rent_amount is
     * {@code NOT NULL} in the schema, so a missing one is a data fault rather than user input; the
     * customer-supplied ones are the dates. end_date is required at signing on purpose: Billing's
     * contract_info, prorating (spec 11.2) and the ContractSigned payload all need a closed term,
     * and the draft endpoint lets QUAN_LY leave it null until then.
     */
    private static void validateSigningFields(Contract contract) {
        if (contract.getStartDate() == null || contract.getEndDate() == null) {
            throw new BusinessException("CONTRACT_INCOMPLETE",
                    "Hop dong thieu ngay bat dau hoac ngay ket thuc", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!contract.getEndDate().isAfter(contract.getStartDate())) {
            throw new BusinessException("INVALID_DATE", "Ngay ket thuc phai sau ngay bat dau",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (contract.getRentAmount() == null || contract.getRentAmount().signum() <= 0) {
            throw new BusinessException("CONTRACT_INCOMPLETE", "Hop dong thieu gia thue hop le",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /** Spec 9.4: the original customer becomes the first occupant, representative, from start_date. */
    private static ContractTenant initialRepresentative(Contract contract) {
        ContractTenant tenant = new ContractTenant();
        tenant.setContractId(contract.getId());
        tenant.setCustomerId(contract.getCustomerId());
        tenant.setJoinedAt(contract.getStartDate());
        tenant.setLeftAt(null);
        tenant.setRepresentative(true);
        return tenant;
    }

    /** Advisory-lock key; the prefix keeps room and customer namespaces apart on the same hash. */
    private static String signKey(String kind, Long id) {
        return kind + ":" + id;
    }

    private static BigDecimal normalizeMoney(BigDecimal amount) {
        return amount.stripTrailingZeros().scale() < 0 ? amount.setScale(0) : amount;
    }
}
