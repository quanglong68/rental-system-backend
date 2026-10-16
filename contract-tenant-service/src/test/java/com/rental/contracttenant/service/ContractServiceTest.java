package com.rental.contracttenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rental.common.error.BusinessException;
import com.rental.contracttenant.dto.ContractResponse;
import com.rental.contracttenant.dto.CreateDraftContractRequest;
import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.repository.ContractRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class ContractServiceTest {

    @Mock
    private ContractRepository contracts;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ContractService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        service = new ContractService(contracts, transactionManager);
    }

    @Test
    void createDraft_firstRequest_mapsAcceptedRequestAndDefaultsStatus() {
        CreateDraftContractRequest request = request();
        when(contracts.findByRentalRequestId(41L)).thenReturn(Optional.empty());
        when(contracts.save(any(Contract.class))).thenAnswer(invocation -> {
            Contract saved = invocation.getArgument(0);
            saved.setId(9L);
            return saved;
        });

        ContractResponse response = service.createDraft(request);

        ArgumentCaptor<Contract> captor = ArgumentCaptor.forClass(Contract.class);
        verify(contracts).save(captor.capture());
        Contract saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(ContractStatus.DRAFT);
        assertThat(saved.getRentalRequestId()).isEqualTo(41L);
        assertThat(saved.getRoomId()).isEqualTo(12L);
        assertThat(saved.getBuildingId()).isEqualTo(3L);
        assertThat(saved.getCustomerId()).isEqualTo(77L);
        assertThat(saved.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(saved.getRentAmount()).isEqualByComparingTo("3500000");
        assertThat(response.getId()).isEqualTo(9L);
    }

    @Test
    void createDraft_sequentialReplay_returnsExistingWithoutSecondSave() {
        Contract existing = fixture();
        when(contracts.findByRentalRequestId(41L)).thenReturn(Optional.of(existing));

        ContractResponse response = service.createDraft(request());

        assertThat(response.getId()).isEqualTo(9L);
        assertThat(response.getStatus()).isEqualTo(ContractStatus.DRAFT);
        verify(contracts, never()).save(any(Contract.class));
    }

    @Test
    void createDraft_sequentialReplayWithDifferentData_isRejectedWithConflict() {
        Contract existing = fixture();
        when(contracts.findByRentalRequestId(41L)).thenReturn(Optional.of(existing));

        CreateDraftContractRequest replay = request();
        replay.setPrice(new BigDecimal("3600000"));

        assertThatThrownBy(() -> service.createDraft(replay))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("RENTAL_REQUEST_CONFLICT"));
        verify(contracts, never()).save(any(Contract.class));
    }

    @Test
    void createDraft_concurrentRaceWithSamePayload_reloadsAndReturnsExisting() {
        CreateDraftContractRequest request = request();
        Contract existingCommitted = fixture();

        // Initial lookup finds nothing, save throws DataIntegrityViolationException (another tx won the race)
        when(contracts.findByRentalRequestId(41L))
                .thenReturn(Optional.empty()) // in initial tx
                .thenReturn(Optional.of(existingCommitted)); // in recovery tx reload
        when(contracts.save(any(Contract.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        ContractResponse response = service.createDraft(request);

        assertThat(response.getId()).isEqualTo(9L);
        assertThat(response.getStatus()).isEqualTo(ContractStatus.DRAFT);
    }

    @Test
    void createDraft_concurrentRaceWithConflictingPayload_reloadsAndThrowsConflict() {
        CreateDraftContractRequest conflictingRequest = request();
        conflictingRequest.setPrice(new BigDecimal("4000000"));
        Contract existingCommitted = fixture();

        when(contracts.findByRentalRequestId(41L))
                .thenReturn(Optional.empty()) // in initial tx
                .thenReturn(Optional.of(existingCommitted)); // in recovery tx reload
        when(contracts.save(any(Contract.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> service.createDraft(conflictingRequest))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("RENTAL_REQUEST_CONFLICT"));
    }

    @Test
    void createDraft_unrelatedDatabaseIntegrityViolation_isRethrown() {
        CreateDraftContractRequest request = request();

        // Initial lookup finds nothing, save throws integrity violation, but reload finds nothing
        when(contracts.findByRentalRequestId(41L))
                .thenReturn(Optional.empty()) // in initial tx
                .thenReturn(Optional.empty()); // in recovery reload
        when(contracts.save(any(Contract.class)))
                .thenThrow(new DataIntegrityViolationException("check constraint failed or other integrity issue"));

        assertThatThrownBy(() -> service.createDraft(request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createDraft_nullRequest_throwsValidationException() {
        assertThatThrownBy(() -> service.createDraft(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("VALIDATION_ERROR"));
    }

    private static CreateDraftContractRequest request() {
        CreateDraftContractRequest request = new CreateDraftContractRequest();
        request.setRentalRequestId(41L);
        request.setListingId(17L);
        request.setRoomId(12L);
        request.setBuildingId(3L);
        request.setCustomerId(77L);
        request.setMoveInDate(LocalDate.of(2026, 11, 1));
        request.setPrice(new BigDecimal("3500000"));
        return request;
    }

    private static Contract fixture() {
        Contract c = new Contract();
        c.setId(9L);
        c.setRentalRequestId(41L);
        c.setStatus(ContractStatus.DRAFT);
        c.setRoomId(12L);
        c.setBuildingId(3L);
        c.setCustomerId(77L);
        c.setStartDate(LocalDate.of(2026, 11, 1));
        c.setRentAmount(new BigDecimal("3500000"));
        return c;
    }
}
