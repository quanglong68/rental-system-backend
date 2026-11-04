package com.rental.contracttenant.scheduler;

import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.entity.ContractTenant;
import com.rental.contracttenant.entity.MoveOutRequest;
import com.rental.contracttenant.entity.MoveOutStatus;
import com.rental.contracttenant.entity.SignatureStatus;
import com.rental.contracttenant.outbox.ContractEndedPayload;
import com.rental.contracttenant.outbox.OutboxEventPublisher;
import com.rental.contracttenant.config.ContractRenewalSettings;
import com.rental.contracttenant.repository.ContractRepository;
import com.rental.contracttenant.repository.ContractTenantRepository;
import com.rental.contracttenant.repository.MoveOutRequestRepository;
import com.rental.contracttenant.service.ContractService;
import java.time.Clock;
import java.time.Instant;
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
    private final ContractService contractService;
    private final ContractRenewalSettings renewalSettings;
    private final OutboxEventPublisher outbox;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void processExpiredContracts() {
        expireSignatureDeadlines();

        LocalDate today = LocalDate.now(clock);
        log.info("ContractNightlyScheduler bắt đầu xử lý hợp đồng hết hạn cho ngày {}", today);

        createAutoMoveOuts(today);

        List<Contract> expiredContracts = contracts.findExpiredActiveContracts(ContractStatus.ACTIVE, today);
        for (Contract c : expiredContracts) {
            try {
                processSingleExpiredContract(c.getId());
            } catch (Exception e) {
                log.error("Lỗi khi xử lý kết thúc hợp đồng {}: {}", c.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Renewals are tenant-initiated now: a request must be approved by a manager before any draft
     * exists, so the scheduler no longer silently creates renewal drafts.
     */
    /**
     * Spec 13.5: an unsigned renewal inside the move-out window becomes an approved move-out so the
     * room can be re-listed with the cleaning buffer after end_date.
     */
    private void createAutoMoveOuts(LocalDate today) {
        List<Contract> ending = contracts.findByStatusAndEndDateBetween(
                ContractStatus.ACTIVE, today, today.plusDays(renewalSettings.moveOutDays()));
        for (Contract c : ending) {
            try {
                contractService.createAutoMoveOut(c.getId(), today);
            } catch (Exception e) {
                log.error("Lỗi khi tạo yêu cầu trả phòng tự động cho hợp đồng {}: {}",
                        c.getId(), e.getMessage(), e);
            }
        }
    }

    /** Spec 9.x: a draft nobody signed within the deadline is cancelled and its room can be re-listed. */
    private void expireSignatureDeadlines() {
        List<Contract> expired = contracts.findExpiredSignatureContracts(
                List.of(SignatureStatus.AWAITING_TENANT, SignatureStatus.AWAITING_LESSOR), Instant.now(clock));
        for (Contract c : expired) {
            try {
                contractService.expireSignatureContract(c.getId());
            } catch (Exception e) {
                log.error("Lỗi khi hủy hợp đồng quá hạn ký {}: {}", c.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * 00:05 hang ngay: nhac ky thuat ghi chi so ban giao cho hop dong da ACTIVE co ngay chuyen vao
     * la hom nay. Hop dong ky truoc ngay vao cung duoc quet o day; outbox chi giu mot event/hop dong.
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Ho_Chi_Minh")
    public void remindHandoverReadings() {
        LocalDate today = LocalDate.now(clock);
        List<Contract> moveIns = contracts.findByStatusAndStartDate(ContractStatus.ACTIVE, today);
        for (Contract c : moveIns) {
            try {
                TransactionTemplate template = new TransactionTemplate(transactionManager);
                template.executeWithoutResult(status -> outbox.publishMeterReadingHandoverDue(c));
            } catch (Exception e) {
                log.error("Lỗi khi gửi nhắc bàn giao cho hợp đồng {}: {}", c.getId(), e.getMessage(), e);
            }
        }
        if (!moveIns.isEmpty()) {
            log.info("Đã nhắc ghi chỉ số bàn giao cho {} hợp đồng chuyển vào {}", moveIns.size(), today);
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
            locked.setRevision(locked.getRevision() + 1);
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
                    locked.getStatus().name(), nextContractExists, tenantIds, locked.getRevision()
            ));
        });
    }
}
