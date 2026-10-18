package com.rental.contracttenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rental.common.error.BusinessException;
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
import java.time.LocalDate;
import java.util.List;
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
    private ContractTenantRepository tenants;

    @Mock
    private OutboxEventPublisher outbox;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ContractService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        service = new ContractService(contracts, tenants, outbox, transactionManager);
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

    @Test
    void updateDraft_keepsIdentityFieldsAndAppliesOnlyNegotiableTerms() {
        Contract existing = fixture();
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(existing));
        when(contracts.save(existing)).thenReturn(existing);
        UpdateDraftContractRequest request = updateRequest();

        ContractResponse response = service.updateDraftDetails(9L, request);

        assertThat(response.getStartDate()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(response.getEndDate()).isEqualTo(LocalDate.of(2027, 11, 30));
        assertThat(response.getRentAmount()).isEqualByComparingTo("4000000");
        assertThat(response.getDepositAmount()).isEqualByComparingTo("1500000");
        assertThat(response.getRentalRequestId()).isEqualTo(41L);
        assertThat(response.getRoomId()).isEqualTo(12L);
        assertThat(response.getBuildingId()).isEqualTo(3L);
        assertThat(response.getCustomerId()).isEqualTo(77L);
        assertThat(response.getStatus()).isEqualTo(ContractStatus.DRAFT);
    }

    @Test
    void updateDraft_missingContract_throwsNotFound() {
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateDraftDetails(9L, updateRequest()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("CONTRACT_NOT_FOUND"));
        verify(contracts, never()).save(any(Contract.class));
    }

    @Test
    void updateDraft_nonDraftContract_throwsInvalidStatus() {
        Contract active = fixture();
        active.setStatus(ContractStatus.ACTIVE);
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.updateDraftDetails(9L, updateRequest()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("INVALID_STATUS"));
        verify(contracts, never()).save(any(Contract.class));
    }

    @Test
    void updateDraft_endDateBeforeStartDate_throwsInvalidDate() {
        UpdateDraftContractRequest request = updateRequest();
        request.setEndDate(LocalDate.of(2026, 11, 30));

        assertThatThrownBy(() -> service.updateDraftDetails(9L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("INVALID_DATE"));
        verify(contracts, never()).findByIdForUpdate(9L);
    }

    @Test
    void updateDraft_endDateEqualToStartDate_throwsInvalidDate() {
        // A zero-length term is not a valid rental period ("phai sau ngay bat dau").
        UpdateDraftContractRequest request = updateRequest();
        request.setEndDate(request.getStartDate());

        assertThatThrownBy(() -> service.updateDraftDetails(9L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("INVALID_DATE"));
        verify(contracts, never()).findByIdForUpdate(9L);
    }

    @Test
    void updateDraft_nullStartDate_throwsValidationErrorNotNullPointer() {
        // Bean validation should catch this at the HTTP boundary, but a service-level caller can
        // reach this method directly; it must fail with the project's error, not an NPE.
        UpdateDraftContractRequest request = updateRequest();
        request.setStartDate(null);

        assertThatThrownBy(() -> service.updateDraftDetails(9L, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("VALIDATION_ERROR"));
        verify(contracts, never()).findByIdForUpdate(9L);
    }

    @Test
    void updateDraft_blankEndAndDepositDetails_storesNulls() {
        Contract existing = fixture();
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(existing));
        when(contracts.save(existing)).thenReturn(existing);
        UpdateDraftContractRequest request = updateRequest();
        request.setEndDate(null);
        request.setDepositAmount(null);

        ContractResponse response = service.updateDraftDetails(9L, request);

        assertThat(response.getEndDate()).isNull();
        assertThat(response.getDepositAmount()).isNull();
    }

    @Test
    void updateDraft_nullRequest_throwsValidationException() {
        assertThatThrownBy(() -> service.updateDraftDetails(9L, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("VALIDATION_ERROR"));
    }

    private static UpdateDraftContractRequest updateRequest() {
        UpdateDraftContractRequest request = new UpdateDraftContractRequest();
        request.setStartDate(LocalDate.of(2026, 12, 1));
        request.setEndDate(LocalDate.of(2027, 11, 30));
        request.setRentAmount(new BigDecimal("4000000"));
        request.setDepositAmount(new BigDecimal("1500000"));
        return request;
    }

    // ---- signing ----

    /** A complete DRAFT: signed fixtures always carry a closed term, since signing requires one. */
    private static Contract signable() {
        Contract c = fixture();
        c.setEndDate(LocalDate.of(2027, 10, 31));
        return c;
    }

    /** Stubs both reads and the two cross-contract checks to "nothing in the way". */
    private void allowSigning(Contract contract) {
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(contracts.existsActiveOverlappingContract(any(), any(), any(), any(), any())).thenReturn(false);
        when(tenants.existsCurrentTenantInContractWithStatus(any(), any())).thenReturn(false);
    }

    @Test
    void sign_draftContract_activatesAndCreatesOneRepresentativeFromStartDate() {
        Contract contract = signable();
        allowSigning(contract);
        when(contracts.save(contract)).thenReturn(contract);

        ContractResponse response = service.sign(9L);

        assertThat(response.getStatus()).isEqualTo(ContractStatus.ACTIVE);

        ArgumentCaptor<ContractTenant> tenant = ArgumentCaptor.forClass(ContractTenant.class);
        verify(tenants).save(tenant.capture());
        assertThat(tenant.getValue().getContractId()).isEqualTo(9L);
        assertThat(tenant.getValue().getCustomerId()).isEqualTo(77L);
        assertThat(tenant.getValue().getJoinedAt()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(tenant.getValue().getLeftAt()).isNull();
        assertThat(tenant.getValue().isRepresentative()).isTrue();

        // The original applicant reference on the contract is untouched by signing.
        assertThat(contract.getCustomerId()).isEqualTo(77L);
    }

    @Test
    void sign_persistsContractSignedEventInSameTransactionAsTheStatusChange() {
        Contract contract = signable();
        allowSigning(contract);
        when(contracts.save(contract)).thenReturn(contract);

        service.sign(9L);

        verify(outbox).publishContractSigned(eq(contract), eq(List.of(77L)));
    }

    @Test
    void sign_takesCrossContractAdvisoryLocksBeforeTheRowLock() {
        Contract contract = signable();
        allowSigning(contract);
        when(contracts.save(contract)).thenReturn(contract);

        service.sign(9L);

        // Serialization of two different contracts of the same customer or room depends on these.
        // Customer before room: the fixed order that keeps two signings from deadlocking.
        var order = org.mockito.Mockito.inOrder(contracts);
        order.verify(contracts).lockNamedKey("customer:77");
        order.verify(contracts).lockNamedKey("room:12");
    }

    @Test
    void sign_missingContract_throwsNotFound() {
        when(contracts.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sign(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("CONTRACT_NOT_FOUND"));
        verify(tenants, never()).save(any());
        verify(outbox, never()).publishContractSigned(any(), any());
        verify(contracts, never()).lockNamedKey(anyString());
    }

    @Test
    void sign_nonDraftContract_throwsInvalidStatusAndWritesNothing() {
        Contract active = signable();
        active.setStatus(ContractStatus.TERMINATED);
        when(contracts.findById(9L)).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.sign(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("INVALID_STATUS"));
        verify(contracts, never()).save(any());
        verify(outbox, never()).publishContractSigned(any(), any());
    }

    @Test
    void sign_nonDraftAndIncomplete_reportsInvalidStatusNotContractIncomplete() {
        // Both rules broken at once: TERMINATED and no endDate. Status must win, because
        // CONTRACT_INCOMPLETE would tell the caller to fix the term and retry, and that retry can
        // never succeed on a contract that is no longer DRAFT.
        Contract terminatedAndIncomplete = fixture(); // endDate stays null
        terminatedAndIncomplete.setStatus(ContractStatus.TERMINATED);
        when(contracts.findById(9L)).thenReturn(Optional.of(terminatedAndIncomplete));

        assertThatThrownBy(() -> service.sign(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("INVALID_STATUS"));
        verify(contracts, never()).lockNamedKey(anyString());
        verify(tenants, never()).save(any());
        verify(outbox, never()).publishContractSigned(any(), any());
    }

    @Test
    void sign_retriedAfterCommit_isRejectedWithoutDuplicateTenantOrEvent() {
        // A retry sees the committed ACTIVE row; the DRAFT-only rule is what prevents a second
        // contract_tenant and a second logical ContractSigned.
        Contract nowActive = signable();
        nowActive.setStatus(ContractStatus.ACTIVE);
        when(contracts.findById(9L)).thenReturn(Optional.of(nowActive));

        assertThatThrownBy(() -> service.sign(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("INVALID_STATUS"));
        verify(tenants, never()).save(any());
        verify(outbox, never()).publishContractSigned(any(), any());
    }

    @Test
    void sign_missingEndDate_throwsContractIncomplete() {
        Contract incomplete = fixture(); // endDate stays null
        when(contracts.findById(9L)).thenReturn(Optional.of(incomplete));

        assertThatThrownBy(() -> service.sign(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("CONTRACT_INCOMPLETE"));
        verify(contracts, never()).lockNamedKey(anyString());
    }

    @Test
    void sign_nonPositiveRentAmount_throwsContractIncomplete() {
        Contract incomplete = signable();
        incomplete.setRentAmount(BigDecimal.ZERO);
        when(contracts.findById(9L)).thenReturn(Optional.of(incomplete));

        assertThatThrownBy(() -> service.sign(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("CONTRACT_INCOMPLETE"));
    }

    @Test
    void sign_endDateEqualToStartDate_throwsInvalidDate() {
        Contract zeroTerm = signable();
        zeroTerm.setEndDate(zeroTerm.getStartDate());
        when(contracts.findById(9L)).thenReturn(Optional.of(zeroTerm));

        assertThatThrownBy(() -> service.sign(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("INVALID_DATE"));
        verify(contracts, never()).lockNamedKey(anyString());
    }

    @Test
    void sign_overlappingActiveContractForRoom_throwsRoomAlreadyContracted() {
        Contract contract = signable();
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(contracts.existsActiveOverlappingContract(any(), any(), any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.sign(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("ROOM_ALREADY_CONTRACTED"));
        verify(contracts, never()).save(any());
        verify(outbox, never()).publishContractSigned(any(), any());
    }

    @Test
    void sign_customerAlreadyInAnotherActiveContract_throwsCustomerAlreadyInRoom() {
        Contract contract = signable();
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(contracts.existsActiveOverlappingContract(any(), any(), any(), any(), any())).thenReturn(false);
        when(tenants.existsCurrentTenantInContractWithStatus(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.sign(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("CUSTOMER_ALREADY_IN_ROOM"));
        verify(tenants, never()).save(any());
    }

    @Test
    void sign_statusChangedWhileWaitingForTheLock_isRejectedOnTheReload() {
        // First read is DRAFT so the request gets as far as the lock; by the time the row lock is
        // granted another transaction has signed it.
        Contract staleDraft = signable();
        Contract committedActive = signable();
        committedActive.setStatus(ContractStatus.ACTIVE);
        when(contracts.findById(9L)).thenReturn(Optional.of(staleDraft));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(committedActive));

        assertThatThrownBy(() -> service.sign(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("INVALID_STATUS"));
        verify(contracts, never()).save(any());
    }

    @Test
    void sign_tenantInsertLosesRace_mapsConstraintViolationToConflictNotServerError() {
        Contract contract = signable();
        allowSigning(contract);
        when(contracts.save(contract)).thenReturn(contract);
        // V2 partial unique index uq_contract_tenant_active_customer fires.
        when(tenants.save(any(ContractTenant.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> service.sign(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("CUSTOMER_ALREADY_IN_ROOM"));
        // The ContractSigned row is only written after the tenant insert, so it must not exist.
        verify(outbox, never()).publishContractSigned(any(), any());
    }

    @Test
    void updateDraft_andSign_useTheSameLockedRead() {
        // The shared protocol: if either path went back to a plain findById, the two would race.
        Contract active = signable();
        Contract draft = fixture();
        when(contracts.findById(9L)).thenReturn(Optional.of(active));
        when(contracts.findByIdForUpdate(9L))
                .thenReturn(Optional.of(active))   // sign's locked reload
                .thenReturn(Optional.of(draft));   // the update path's locked read
        when(contracts.save(active)).thenReturn(active);
        when(contracts.save(draft)).thenReturn(draft);
        when(contracts.existsActiveOverlappingContract(any(), any(), any(), any(), any())).thenReturn(false);
        when(tenants.existsCurrentTenantInContractWithStatus(any(), any())).thenReturn(false);

        service.sign(9L);
        service.updateDraftDetails(9L, updateRequest());

        // sign's locked reload + the update path's locked read; both paths reach the row through the
        // locked lookup, and sign's earlier plain read is the only unlocked one.
        verify(contracts, times(1)).findById(9L);
        verify(contracts, times(2)).findByIdForUpdate(9L);
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
