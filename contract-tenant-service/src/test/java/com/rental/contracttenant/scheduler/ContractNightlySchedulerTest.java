package com.rental.contracttenant.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class ContractNightlySchedulerTest {

    /** 2026-10-22T18:00Z == 2026-10-23 01:00 in Asia/Ho_Chi_Minh (UTC+7). */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T18:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));

    @Mock
    private ContractRepository contractRepository;
    @Mock
    private ContractTenantRepository contractTenantRepository;
    @Mock
    private MoveOutRequestRepository moveOutRequestRepository;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;
    @Mock
    private PlatformTransactionManager transactionManager;

    private static final List<MoveOutStatus> OPEN_MOVE_OUTS =
            List.of(MoveOutStatus.PENDING, MoveOutStatus.APPROVED);

    private ContractNightlyScheduler scheduler() {
        return new ContractNightlyScheduler(
                contractRepository,
                contractTenantRepository,
                moveOutRequestRepository,
                outboxEventPublisher,
                transactionManager,
                CLOCK
        );
    }

    @Test
    void processExpiredContracts_noExpired_nothingUpdated() {
        ContractNightlyScheduler scheduler = scheduler();
        when(contractRepository.findExpiredActiveContracts(any(), any())).thenReturn(List.of());

        scheduler.processExpiredContracts();

        verify(contractRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publishContractEnded(any());
    }

    @Test
    void processExpiredContracts_expiredContract_updatesStatusAndPublishesEnded() {
        ContractNightlyScheduler scheduler = scheduler();
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        Contract contract = contract(ContractStatus.ACTIVE, LocalDate.of(2026, 10, 1));

        ContractTenant tenant = new ContractTenant();
        tenant.setContractId(10L);
        tenant.setCustomerId(50L);
        tenant.setRepresentative(true);
        tenant.setJoinedAt(LocalDate.of(2025, 10, 1));

        MoveOutRequest moveOut = new MoveOutRequest();
        moveOut.setStatus(MoveOutStatus.PENDING);

        when(contractRepository.findExpiredActiveContracts(any(), any())).thenReturn(List.of(contract));
        when(contractRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(contract));
        when(contractTenantRepository.findByContractIdAndLeftAtIsNullOrderByJoinedAtAsc(10L))
                .thenReturn(List.of(tenant));
        when(moveOutRequestRepository.findByContractIdAndStatusIn(10L, OPEN_MOVE_OUTS))
                .thenReturn(List.of(moveOut));
        when(contractRepository.existsNextActiveContract(200L, ContractStatus.ACTIVE, contract.getEndDate())).thenReturn(false);

        scheduler.processExpiredContracts();

        verify(contractRepository).save(contract);
        verify(contractTenantRepository).save(tenant);
        verify(moveOutRequestRepository).save(moveOut);
        verify(outboxEventPublisher).publishContractEnded(any());
        assertThat(contract.getStatus()).isEqualTo(ContractStatus.ENDED);
        assertThat(tenant.getLeftAt()).isEqualTo(contract.getEndDate());
        assertThat(moveOut.getStatus()).isEqualTo(MoveOutStatus.COMPLETED);
    }

    @Test
    void processExpiredContracts_terminationReasonRecorded_marksTerminated() {
        ContractNightlyScheduler scheduler = scheduler();
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        Contract contract = contract(ContractStatus.ACTIVE, LocalDate.of(2026, 10, 1));
        contract.setTerminationReason("Khach chuyen di som");

        when(contractRepository.findExpiredActiveContracts(any(), any())).thenReturn(List.of(contract));
        when(contractRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(contract));
        when(contractTenantRepository.findByContractIdAndLeftAtIsNullOrderByJoinedAtAsc(10L))
                .thenReturn(List.of());
        when(moveOutRequestRepository.findByContractIdAndStatusIn(10L, OPEN_MOVE_OUTS))
                .thenReturn(List.of());
        when(contractRepository.existsNextActiveContract(200L, ContractStatus.ACTIVE, contract.getEndDate())).thenReturn(false);

        scheduler.processExpiredContracts();

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.TERMINATED);
        ArgumentCaptor<ContractEndedPayload> payload = ArgumentCaptor.forClass(ContractEndedPayload.class);
        verify(outboxEventPublisher).publishContractEnded(payload.capture());
        assertThat(payload.getValue().getStatus()).isEqualTo("TERMINATED");
    }

    /**
     * "Today" must come from the configured zone, not the machine/UTC zone. The instant is
     * 2026-10-22T18:00Z: UTC says 2026-10-22, Asia/Ho_Chi_Minh says 2026-10-23. A contract ending
     * 2026-10-22 is therefore expired only when the Vietnam zone is used.
     */
    @Test
    void processExpiredContracts_usesConfiguredZoneNotMachineZone() {
        ContractNightlyScheduler scheduler = scheduler();
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        Contract contract = contract(ContractStatus.ACTIVE, LocalDate.of(2026, 10, 22));

        when(contractRepository.findExpiredActiveContracts(eq(ContractStatus.ACTIVE), eq(LocalDate.of(2026, 10, 23))))
                .thenReturn(List.of(contract));
        when(contractRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(contract));
        when(contractTenantRepository.findByContractIdAndLeftAtIsNullOrderByJoinedAtAsc(10L))
                .thenReturn(List.of());
        when(moveOutRequestRepository.findByContractIdAndStatusIn(10L, OPEN_MOVE_OUTS))
                .thenReturn(List.of());
        when(contractRepository.existsNextActiveContract(200L, ContractStatus.ACTIVE, contract.getEndDate())).thenReturn(false);

        scheduler.processExpiredContracts();

        assertThat(contract.getStatus()).isEqualTo(ContractStatus.ENDED);
        verify(outboxEventPublisher).publishContractEnded(any());
    }

    @Test
    void processExpiredContracts_endDateIsToday_nothingHappens() {
        ContractNightlyScheduler scheduler = scheduler();
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        // Ends exactly "today" (Vietnam date); the contract is still valid until the day passes.
        Contract contract = contract(ContractStatus.ACTIVE, LocalDate.of(2026, 10, 23));
        when(contractRepository.findExpiredActiveContracts(any(), any())).thenReturn(List.of(contract));
        when(contractRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(contract));

        scheduler.processExpiredContracts();

        verify(contractRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publishContractEnded(any());
    }

    @Test
    void processExpiredContracts_alreadyEnded_doesNotPublishAgain() {
        ContractNightlyScheduler scheduler = scheduler();
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        Contract contract = contract(ContractStatus.ENDED, LocalDate.of(2026, 10, 1));
        when(contractRepository.findExpiredActiveContracts(any(), any())).thenReturn(List.of(contract));
        when(contractRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(contract));

        scheduler.processExpiredContracts();

        verify(contractRepository, never()).save(any());
        verify(outboxEventPublisher, never()).publishContractEnded(any());
    }

    private static Contract contract(ContractStatus status, LocalDate endDate) {
        Contract contract = new Contract();
        contract.setId(10L);
        contract.setBuildingId(100L);
        contract.setRoomId(200L);
        contract.setCustomerId(50L);
        contract.setStatus(status);
        contract.setStartDate(LocalDate.of(2025, 10, 1));
        contract.setEndDate(endDate);
        contract.setRentAmount(new BigDecimal("4000000"));
        contract.setDepositAmount(new BigDecimal("4000000"));
        return contract;
    }
}
