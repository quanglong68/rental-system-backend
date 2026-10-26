package com.rental.contracttenant.scheduler;

import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.entity.ContractTenant;
import com.rental.contracttenant.entity.MoveOutRequest;
import com.rental.contracttenant.entity.MoveOutStatus;
import com.rental.contracttenant.outbox.ContractEndedPayload;
import com.rental.contracttenant.outbox.OutboxEventPublisher;
import com.rental.contracttenant.repository.ContractRepository;
import com.rental.contracttenant.repository.ContractTenantRepository;
import com.rental.contracttenant.repository.MoveOutRequestRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spec 9.4 "Tự kết thúc theo ngày": runs daily at 00:10 Vietnam time. An ACTIVE contract whose
 * end_date has passed becomes TERMINATED when it was cut short via the terminate API (a termination
 * reason is recorded), otherwise ENDED. Occupants get left_at = end_date, move-out requests still
 * open become COMPLETED, and ContractEnded is emitted exactly once because the status guard runs
 * under the row lock.
 *
 * <p>"Today" comes from the injected clock (Asia/Ho_Chi_Minh), never from the JVM default zone.
 */
@Component
@RequiredArgsConstructor
public class ContractNightlyScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContractNightlyScheduler.class);

    /** Move-out requests still awaiting an outcome when the contract closes; PENDING or APPROVED. */
    private static final List<MoveOutStatus> OPEN_MOVE_OUT_STATUSES =
            List.of(MoveOutStatus.PENDING, MoveOutStatus.APPROVED);

    private final ContractRepository contracts;
    private final ContractTenantRepository tenants;
    private final MoveOutRequestRepository moveOutRequests;
    private final OutboxEventPublisher outbox;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void processExpiredContracts() {
        LocalDate today = LocalDate.now(clock);
        log.info("ContractNightlyScheduler bat dau xu ly hop dong het han cho ngay {}", today);

        List<Contract> expiredContracts = contracts.findExpiredActiveContracts(ContractStatus.ACTIVE, today);
        for (Contract c : expiredContracts) {
            try {
                processSingleExpiredContract(c.getId());
            } catch (Exception e) {
                log.error("Loi khi xu ly ket thuc hop dong {}: {}", c.getId(), e.getMessage(), e);
            }
        }
    }

    public void processSingleExpiredContract(Long contractId) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            Contract locked = contracts.findByIdForUpdate(contractId)
                    .orElse(null);
            if (locked == null || locked.getStatus() != ContractStatus.ACTIVE) {
                return;
            }

            LocalDate today = LocalDate.now(clock);
            if (locked.getEndDate() == null || !locked.getEndDate().isBefore(today)) {
                return;
            }

            boolean terminatedEarly = locked.getTerminationReason() != null && !locked.getTerminationReason().isBlank();
            locked.setStatus(terminatedEarly ? ContractStatus.TERMINATED : ContractStatus.ENDED);
            contracts.save(locked);

            List<ContractTenant> activeOccupants = tenants.findByContractIdAndLeftAtIsNullOrderByJoinedAtAsc(locked.getId());
            for (ContractTenant occupant : activeOccupants) {
                occupant.setLeftAt(locked.getEndDate());
                tenants.save(occupant);
            }

            List<MoveOutRequest> openMoveOuts = moveOutRequests
                    .findByContractIdAndStatusIn(locked.getId(), OPEN_MOVE_OUT_STATUSES);
            for (MoveOutRequest mor : openMoveOuts) {
                mor.setStatus(MoveOutStatus.COMPLETED);
                moveOutRequests.save(mor);
            }

            boolean nextContractExists = contracts.existsNextActiveContract(locked.getRoomId(), ContractStatus.ACTIVE, locked.getEndDate());

            List<Long> tenantIds = activeOccupants.stream().map(ContractTenant::getCustomerId).toList();
            outbox.publishContractEnded(new ContractEndedPayload(
                    locked.getId(), locked.getRoomId(), locked.getBuildingId(), locked.getEndDate(),
                    locked.getStatus().name(), nextContractExists, tenantIds
            ));
        });
    }
}
