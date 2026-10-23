package com.rental.contracttenant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rental.common.error.BusinessException;
import com.rental.common.error.DependencyUnavailableException;
import com.rental.common.security.HeaderUser;
import com.rental.contracttenant.client.PropertyClient;
import com.rental.contracttenant.dto.AddTenantRequest;
import com.rental.contracttenant.dto.ContractResponse;
import com.rental.contracttenant.dto.ContractTenantResponse;
import com.rental.contracttenant.dto.CreateDraftContractRequest;
import com.rental.contracttenant.dto.TenantLeaveRequest;
import com.rental.contracttenant.dto.UpdateDraftContractRequest;
import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.entity.ContractTenant;
import com.rental.contracttenant.entity.MoveOutRequest;
import com.rental.contracttenant.entity.MoveOutStatus;
import com.rental.contracttenant.outbox.OutboxEventPublisher;
import com.rental.contracttenant.outbox.TenantRemovedPayload;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private MoveOutRequestRepository moveOutRequests;

    @Mock
    private OutboxEventPublisher outbox;

    @Mock
    private PropertyClient properties;

    @Mock
    private BuildingScope buildingScope;

    @Mock
    private PlatformTransactionManager transactionManager;

    /** Fixed at 2026-10-22T05:00Z == 2026-10-22 12:00 Asia/Ho_Chi_Minh. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T05:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));

    private ContractService service;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        lenient().when(contracts.findById(9L)).thenReturn(Optional.of(fixture()));
        service = new ContractService(contracts, tenants, moveOutRequests, outbox, properties, buildingScope,
                transactionManager, CLOCK);
    }

    @org.junit.jupiter.api.AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
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
        when(contracts.findById(9L)).thenReturn(Optional.empty());

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
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uq_contract_tenant_active_customer\""));

        assertThatThrownBy(() -> service.sign(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("CUSTOMER_ALREADY_IN_ROOM"));
        // The ContractSigned row is only written after the tenant insert, so it must not exist.
        verify(outbox, never()).publishContractSigned(any(), any());
    }

    @Test
    void sign_unrelatedIntegrityFailure_isNotRelabelledAsADuplicate() {
        Contract contract = signable();
        allowSigning(contract);
        when(contracts.save(contract)).thenReturn(contract);
        when(tenants.save(any(ContractTenant.class))).thenThrow(new DataIntegrityViolationException(
                "insert or update violates foreign key constraint \"fk_contract_tenant_contract\""));

        assertThatThrownBy(() -> service.sign(9L)).isInstanceOf(DataIntegrityViolationException.class);
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
        // locked lookup. Both paths also do an earlier unlocked findById for building-scope and cheap checks.
        verify(contracts, times(2)).findById(9L);
        verify(contracts, times(2)).findByIdForUpdate(9L);
    }

    // ---- Task 5A: change representative ----

    private static ContractTenant tenant(Long id, Long customerId, boolean representative, LocalDate leftAt) {
        ContractTenant t = new ContractTenant();
        t.setId(id);
        t.setContractId(9L);
        t.setCustomerId(customerId);
        t.setJoinedAt(LocalDate.of(2026, 11, 1));
        t.setLeftAt(leftAt);
        t.setRepresentative(representative);
        return t;
    }

    @Test
    void changeRepresentative_activeTenant_clearsOldFlagBeforeSettingNew() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        ContractTenant current = tenant(31L, 77L, true, null);
        ContractTenant target = tenant(32L, 88L, false, null);
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(tenants.findByIdAndContractId(32L, 9L)).thenReturn(Optional.of(target));
        when(tenants.findById(32L)).thenReturn(Optional.of(target));

        service.changeRepresentative(9L, 32L);

        // The ordering is what keeps uq_contract_tenant_representative satisfiable: the clear must
        // be issued before the flagged write, or the index sees two representatives at once.
        var order = org.mockito.Mockito.inOrder(tenants);
        order.verify(tenants).clearRepresentative(9L);
        order.verify(tenants).save(target);

        assertThat(target.isRepresentative()).isTrue();
        // The old representative is cleared by the bulk UPDATE, never by a second entity save.
        assertThat(current.isRepresentative()).isTrue();
        verify(tenants, never()).save(current);
    }

    @Test
    void changeRepresentative_takesTheContractRowLockAndNoAdvisoryLock() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        ContractTenant target = tenant(32L, 88L, false, null);
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(tenants.findByIdAndContractId(32L, 9L)).thenReturn(Optional.of(target));
        when(tenants.findById(32L)).thenReturn(Optional.of(target));

        service.changeRepresentative(9L, 32L);

        verify(contracts).findByIdForUpdate(9L);
        // No advisory lock here: this path checks neither the customer nor the room constraint, so it
        // must not enter the documented customer -> room -> row order at all.
        verify(contracts, never()).lockNamedKey(anyString());
    }

    @Test
    void changeRepresentative_departedTenant_isRejectedWithoutWriting() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        ContractTenant departed = tenant(32L, 88L, false, LocalDate.of(2027, 1, 31));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(tenants.findByIdAndContractId(32L, 9L)).thenReturn(Optional.of(departed));

        assertThatThrownBy(() -> service.changeRepresentative(9L, 32L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("TENANT_NOT_ACTIVE"));
        // Crucially the clear did not run: a rejected request must leave the contract's existing
        // representative in place.
        verify(tenants, never()).clearRepresentative(any());
        verify(tenants, never()).save(any());
    }

    @Test
    void changeRepresentative_missingContract_throwsNotFound() {
        when(contracts.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRepresentative(9L, 32L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("CONTRACT_NOT_FOUND"));
        verify(tenants, never()).clearRepresentative(any());
    }

    @Test
    void changeRepresentative_tenantOfAnotherContract_throwsTenantNotFound() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        // Scoped lookup by (id, contractId) is what makes this empty; the row exists but under a
        // different contract.
        when(tenants.findByIdAndContractId(32L, 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeRepresentative(9L, 32L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("TENANT_NOT_FOUND"));
        verify(tenants, never()).clearRepresentative(any());
    }

    @Test
    void changeRepresentative_failureAfterClear_rollsTheWholeTransactionBack() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        ContractTenant target = tenant(32L, 88L, false, null);
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(tenants.findByIdAndContractId(32L, 9L)).thenReturn(Optional.of(target));
        when(tenants.findById(32L)).thenReturn(Optional.of(target));
        // The set fails after the clear was issued: the transaction must roll back, not commit a
        // contract with no representative. The integrity violation propagates untranslated (see the
        // method's LIMITATION note): the spec names no code for this case, and inventing one would
        // mislabel any other constraint that happened to fire.
        when(tenants.save(target)).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> service.changeRepresentative(9L, 32L))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Both writes share one transaction, so the clear is rolled back with the failed set and the
        // contract keeps its previous representative. Committed never, rolled back once.
        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void changeRepresentative_targetIsOnlyReadWithinThePathContract_pairing() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        ContractTenant target = tenant(32L, 88L, false, null);
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(tenants.findByIdAndContractId(32L, 9L)).thenReturn(Optional.of(target));
        when(tenants.findById(32L)).thenReturn(Optional.of(target));

        service.changeRepresentative(9L, 32L);

        // Never an unscoped findById for the validation read: that would let a tenant of another
        // contract be promoted.
        verify(tenants).findByIdAndContractId(32L, 9L);
    }

    // ---- Task 5B: add tenant ----

    private static AddTenantRequest addRequest(Long customerId, LocalDate joinedAt) {
        AddTenantRequest request = new AddTenantRequest();
        request.setCustomerId(customerId);
        request.setJoinedAt(joinedAt);
        return request;
    }

    /**
     * Active contract with nothing in the way; capacity answered by the Property stub.
     *
     * <p>Lenient on purpose: this stages the whole happy path, and a test for an earlier guard
     * (not ACTIVE, date before start, duplicate occupant, capacity, Property outage) legitimately
     * returns before the later stubs are reached. Their absence is the test's subject, so strict
     * unused-stub detection would flag the correct behaviour as a defect.
     */
    private void allowAdding(Contract contract, int maxCapacity) {
        contract.setStatus(ContractStatus.ACTIVE);
        lenient().when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        lenient().when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        lenient().when(tenants.existsByContractIdAndCustomerIdAndLeftAtIsNull(eq(9L), any()))
                .thenReturn(false);
        lenient().when(tenants.existsCurrentTenantInContractWithStatus(any(), any())).thenReturn(false);
        lenient().when(properties.getRoom(12L))
                .thenReturn(new PropertyClient.RoomInfo(12L, 3L, maxCapacity));
        lenient().when(tenants.save(any(ContractTenant.class))).thenAnswer(invocation -> {
            ContractTenant saved = invocation.getArgument(0);
            saved.setId(45L);
            return saved;
        });
    }

    @Test
    void addTenant_withinCapacity_insertsActiveOccupantAndReturnsIt() {
        Contract contract = fixture();
        allowAdding(contract, 3);
        when(tenants.countByContractIdAndLeftAtIsNull(9L)).thenReturn(1L);

        ContractTenantResponse response = service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 12, 1)));

        ArgumentCaptor<ContractTenant> captor = ArgumentCaptor.forClass(ContractTenant.class);
        verify(tenants).save(captor.capture());
        ContractTenant saved = captor.getValue();
        assertThat(saved.getContractId()).isEqualTo(9L);
        assertThat(saved.getCustomerId()).isEqualTo(88L);
        assertThat(saved.getJoinedAt()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(saved.getLeftAt()).isNull();
        // Adding a person never promotes them; changing the representative is a separate action.
        assertThat(saved.isRepresentative()).isFalse();
        assertThat(response.getId()).isEqualTo(45L);
    }

    @Test
    void addTenant_atCapacity_isRejectedAndWritesNothing() {
        Contract contract = fixture();
        allowAdding(contract, 3);
        when(tenants.countByContractIdAndLeftAtIsNull(9L)).thenReturn(3L);

        assertThatThrownBy(() -> service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 12, 1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("ROOM_CAPACITY_EXCEEDED"));
        verify(tenants, never()).save(any());
    }

    @Test
    void addTenant_capacityCountedAfterTheRowLock() {
        Contract contract = fixture();
        allowAdding(contract, 3);
        when(tenants.countByContractIdAndLeftAtIsNull(9L)).thenReturn(1L);

        service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 12, 1)));

        // Counting before the lock is the bug this ordering exists to prevent: two adders would both
        // read 1 and both insert into a room of 3.
        var order = org.mockito.Mockito.inOrder(contracts, tenants);
        order.verify(contracts).findByIdForUpdate(9L);
        order.verify(tenants).countByContractIdAndLeftAtIsNull(9L);
        order.verify(tenants).save(any(ContractTenant.class));
    }

    @Test
    void addTenant_contractThatChangedRoomAfterTheLockIsAborted() {
        Contract staleRoom = fixture();
        staleRoom.setStatus(ContractStatus.ACTIVE);
        Contract movedRoom = fixture();
        movedRoom.setStatus(ContractStatus.ACTIVE);
        movedRoom.setRoomId(13L);
        when(contracts.findById(9L)).thenReturn(Optional.of(staleRoom));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(movedRoom));

        // The lock keys came from the pre-lock read; inserting into a contract that moved rooms would
        // put the occupant in a room whose lock was never taken. Abort instead of re-locking.
        assertThatThrownBy(() -> service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 12, 1))))
                .isInstanceOf(IllegalStateException.class);
        verify(tenants, never()).save(any());
    }

    @Test
    void addTenant_takesAdvisoryLocksInTheCustomerThenRoomOrder() {
        Contract contract = fixture();
        allowAdding(contract, 3);
        when(tenants.countByContractIdAndLeftAtIsNull(9L)).thenReturn(0L);

        service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 12, 1)));

        // Same protocol as signing: reversing customer/room deadlocks the two flows against each
        // other, and skipping either lets a cross-contract race through.
        var order = org.mockito.Mockito.inOrder(contracts);
        order.verify(contracts).lockNamedKey("customer:88");
        order.verify(contracts).lockNamedKey("room:12");
        order.verify(contracts).findByIdForUpdate(9L);
    }

    @Test
    void addTenant_customerAlreadyInThisContract_conflicts() {
        Contract contract = fixture();
        allowAdding(contract, 3);
        when(tenants.existsByContractIdAndCustomerIdAndLeftAtIsNull(9L, 88L)).thenReturn(true);

        assertThatThrownBy(() -> service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 12, 1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("CUSTOMER_ALREADY_IN_ROOM"));
        verify(tenants, never()).save(any());
    }

    @Test
    void addTenant_customerInAnotherActiveContract_conflicts() {
        Contract contract = fixture();
        allowAdding(contract, 3);
        when(tenants.existsCurrentTenantInContractWithStatus(88L, ContractStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 12, 1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("CUSTOMER_ALREADY_IN_ROOM"));
        verify(tenants, never()).save(any());
    }

    @Test
    void addTenant_nonActiveContract_isRejectedBeforeTakingAnyLock() {
        Contract draft = fixture(); // DRAFT
        when(contracts.findById(9L)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 11, 5))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("INVALID_STATUS"));
        verify(contracts, never()).lockNamedKey(anyString());
        verify(tenants, never()).save(any());
        // Capacity was never consulted, so the dependency was not touched either.
        verify(properties, never()).getRoom(any());
    }

    @Test
    void addTenant_statusChangedWhileWaitingForTheLock_isRejectedOnTheReload() {
        Contract staleActive = fixture();
        staleActive.setStatus(ContractStatus.ACTIVE);
        Contract committedTerminated = fixture();
        committedTerminated.setStatus(ContractStatus.TERMINATED);
        when(contracts.findById(9L)).thenReturn(Optional.of(staleActive));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(committedTerminated));

        assertThatThrownBy(() -> service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 11, 5))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("INVALID_STATUS"));
        verify(tenants, never()).save(any());
    }

    @Test
    void addTenant_missingContract_throwsNotFound() {
        when(contracts.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 11, 5))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("CONTRACT_NOT_FOUND"));
        verify(tenants, never()).save(any());
    }

    @Test
    void addTenant_propertyServiceFails_closesWith503AndInsertsNothing() {
        Contract contract = fixture();
        allowAdding(contract, 3);
        when(properties.getRoom(12L)).thenThrow(new DependencyUnavailableException("property down"));

        assertThatThrownBy(() -> service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 12, 1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("DEPENDENCY_UNAVAILABLE"));
        verify(tenants, never()).save(any());
        // The capacity was never read, so the request cannot have "passed" it.
        verify(tenants, never()).countByContractIdAndLeftAtIsNull(any());
    }

    @Test
    void addTenant_propertyResponseWithoutCapacity_closesWith503InsteadOfAssumingNoLimit() {
        Contract contract = fixture();
        allowAdding(contract, 3);
        when(properties.getRoom(12L)).thenReturn(new PropertyClient.RoomInfo(12L, 3L, null));

        assertThatThrownBy(() -> service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 12, 1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("DEPENDENCY_UNAVAILABLE"));
        verify(tenants, never()).save(any());
    }

    @Test
    void addTenant_nullPropertyBody_closesWith503() {
        Contract contract = fixture();
        allowAdding(contract, 3);
        when(properties.getRoom(12L)).thenReturn(null);

        assertThatThrownBy(() -> service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 12, 1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("DEPENDENCY_UNAVAILABLE"));
        verify(tenants, never()).save(any());
    }

    @Test
    void addTenant_joinedBeforeContractStart_isRejected() {
        Contract contract = fixture();
        allowAdding(contract, 3);

        assertThatThrownBy(() -> service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 10, 31))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("INVALID_DATE"));
        verify(properties, never()).getRoom(any());
    }

    @Test
    void addTenant_joinedOnContractStart_isAccepted() {
        Contract contract = fixture();
        allowAdding(contract, 3);
        when(tenants.countByContractIdAndLeftAtIsNull(9L)).thenReturn(0L);

        // Inclusive boundary (spec 11.2 counts the move-in day): the start date itself is valid.
        service.addTenant(9L, addRequest(88L, contract.getStartDate()));

        verify(tenants).save(any(ContractTenant.class));
    }

    @Test
    void addTenant_duplicateInsertLostToTheConstraint_mapsToTheDocumentedConflict() {
        Contract contract = fixture();
        allowAdding(contract, 3);
        when(tenants.countByContractIdAndLeftAtIsNull(9L)).thenReturn(0L);
        // The driver's own wording, as Hibernate's PostgreSQL extractor sees it.
        when(tenants.save(any(ContractTenant.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement [ERROR: duplicate key value violates unique constraint "
                        + "\"uq_contract_tenant_active_customer\"]"));

        assertThatThrownBy(() -> service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 12, 1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("CUSTOMER_ALREADY_IN_ROOM"));
    }

    @Test
    void addTenant_mapsOnTheConstraintNameHibernateCarried_notOnMessageWordingAlone() {
        Contract contract = fixture();
        allowAdding(contract, 3);
        when(tenants.countByContractIdAndLeftAtIsNull(9L)).thenReturn(0L);
        // A translated message that no longer contains the index name: only the structured
        // constraint name identifies the violation, and that is what the mapping must use.
        org.hibernate.exception.ConstraintViolationException translated =
                new org.hibernate.exception.ConstraintViolationException(
                        "could not execute statement", new java.sql.SQLException("23505", "23505"),
                        "insert into contract_tenant ...", "uq_contract_tenant_active_customer");
        when(tenants.save(any(ContractTenant.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement", translated));

        assertThatThrownBy(() -> service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 12, 1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("CUSTOMER_ALREADY_IN_ROOM"));
    }

    @Test
    void addTenant_unrelatedIntegrityFailure_isNotRelabelledAsADuplicate() {
        Contract contract = fixture();
        allowAdding(contract, 3);
        when(tenants.countByContractIdAndLeftAtIsNull(9L)).thenReturn(0L);
        when(tenants.save(any(ContractTenant.class))).thenThrow(new DataIntegrityViolationException(
                "insert or update on table \"contract_tenant\" violates foreign key constraint \"fk_other\""));

        // No business code describes this, so it stays an integrity failure (500) instead of telling
        // the caller their customer was already in the room.
        assertThatThrownBy(() -> service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 12, 1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void addTenant_otherUniqueIndexIsNotTreatedAsADuplicateCustomer() {
        Contract contract = fixture();
        allowAdding(contract, 3);
        when(tenants.countByContractIdAndLeftAtIsNull(9L)).thenReturn(0L);
        org.hibernate.exception.ConstraintViolationException wrongIndex =
                new org.hibernate.exception.ConstraintViolationException(
                        "could not execute statement", new java.sql.SQLException("23505", "23505"),
                        "insert into contract_tenant ...", "uq_contract_tenant_representative");
        when(tenants.save(any(ContractTenant.class)))
                .thenThrow(new DataIntegrityViolationException("could not execute statement", wrongIndex));

        // A representative-index collision is unreachable from an insert with is_representative =
        // false, so if it ever fires the invariant broke; reporting it as a duplicate customer would
        // hide that.
        assertThatThrownBy(() -> service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 12, 1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void addTenant_doesNotEmitAnEvent() {
        Contract contract = fixture();
        allowAdding(contract, 3);
        when(tenants.countByContractIdAndLeftAtIsNull(9L)).thenReturn(0L);

        service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 12, 1)));

        // TenantAdded belongs to the roommate flow (spec 1025/1400), which this manual path does not
        // run; writing one here would make Listing close applications this request never opened.
        verify(outbox, never()).publishContractSigned(any(), any());
        verifyNoInteractions(outbox);
    }

    @Test
    void addTenant_nullRequest_isRejectedWithoutTouchingTheDatabase() {
        assertThatThrownBy(() -> service.addTenant(9L, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("VALIDATION_ERROR"));
        verifyNoInteractions(contracts, properties);
    }

    // ---- Building Scope (Task 5D) ----

    @Test
    void updateDraft_assignedBuilding_allowsUpdate() {
        Contract existing = fixture();
        when(contracts.findById(9L)).thenReturn(Optional.of(existing));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(existing));
        when(contracts.save(existing)).thenReturn(existing);

        ContractResponse response = service.updateDraftDetails(9L, updateRequest());

        assertThat(response.getId()).isEqualTo(9L);
        verify(buildingScope).assertAssigned(existing.getBuildingId());
        verify(contracts).save(existing);
    }

    @Test
    void updateDraft_unassignedBuilding_throwsAccessDeniedAndDoesNotMutate() {
        Contract existing = fixture();
        when(contracts.findById(9L)).thenReturn(Optional.of(existing));
        doThrow(new AccessDeniedException("Staff chua duoc phan cong tai toa nha nay"))
                .when(buildingScope).assertAssigned(existing.getBuildingId());

        assertThatThrownBy(() -> service.updateDraftDetails(9L, updateRequest()))
                .isInstanceOf(AccessDeniedException.class);
        verify(contracts, never()).findByIdForUpdate(any());
        verify(contracts, never()).save(any());
    }

    @Test
    void updateDraft_buildingScopeDependencyFails_throws503AndDoesNotMutate() {
        Contract existing = fixture();
        when(contracts.findById(9L)).thenReturn(Optional.of(existing));
        doThrow(new BusinessException("DEPENDENCY_UNAVAILABLE", "Khong kiem tra duoc phan cong toa nha",
                HttpStatus.SERVICE_UNAVAILABLE))
                .when(buildingScope).assertAssigned(existing.getBuildingId());

        assertThatThrownBy(() -> service.updateDraftDetails(9L, updateRequest()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("DEPENDENCY_UNAVAILABLE"));
        verify(contracts, never()).findByIdForUpdate(any());
        verify(contracts, never()).save(any());
    }

    @Test
    void sign_assignedBuilding_allowsSigning() {
        Contract contract = signable();
        allowSigning(contract);
        when(contracts.save(contract)).thenReturn(contract);

        ContractResponse response = service.sign(9L);

        assertThat(response.getStatus()).isEqualTo(ContractStatus.ACTIVE);
        verify(buildingScope).assertAssigned(contract.getBuildingId());
        verify(contracts).save(contract);
    }

    @Test
    void sign_unassignedBuilding_throwsAccessDeniedAndDoesNotMutate() {
        Contract contract = signable();
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        doThrow(new AccessDeniedException("Staff chua duoc phan cong tai toa nha nay"))
                .when(buildingScope).assertAssigned(contract.getBuildingId());

        assertThatThrownBy(() -> service.sign(9L))
                .isInstanceOf(AccessDeniedException.class);
        verify(contracts, never()).lockNamedKey(anyString());
        verify(contracts, never()).findByIdForUpdate(any());
        verify(contracts, never()).save(any());
        verify(tenants, never()).save(any());
    }

    @Test
    void sign_buildingScopeDependencyFails_throws503AndDoesNotMutate() {
        Contract contract = signable();
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        doThrow(new BusinessException("DEPENDENCY_UNAVAILABLE", "Khong kiem tra duoc phan cong toa nha",
                HttpStatus.SERVICE_UNAVAILABLE))
                .when(buildingScope).assertAssigned(contract.getBuildingId());

        assertThatThrownBy(() -> service.sign(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("DEPENDENCY_UNAVAILABLE"));
        verify(contracts, never()).lockNamedKey(anyString());
        verify(contracts, never()).findByIdForUpdate(any());
        verify(contracts, never()).save(any());
        verify(tenants, never()).save(any());
    }

    @Test
    void changeRepresentative_assignedBuilding_allowsChange() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        ContractTenant current = tenant(31L, 77L, true, null);
        ContractTenant target = tenant(32L, 88L, false, null);
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(tenants.findByIdAndContractId(32L, 9L)).thenReturn(Optional.of(target));
        when(tenants.findById(32L)).thenReturn(Optional.of(target));

        service.changeRepresentative(9L, 32L);

        verify(buildingScope).assertAssigned(contract.getBuildingId());
        verify(tenants).clearRepresentative(9L);
        verify(tenants).save(target);
    }

    @Test
    void changeRepresentative_unassignedBuilding_throwsAccessDeniedAndDoesNotMutate() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        doThrow(new AccessDeniedException("Staff chua duoc phan cong tai toa nha nay"))
                .when(buildingScope).assertAssigned(contract.getBuildingId());

        assertThatThrownBy(() -> service.changeRepresentative(9L, 32L))
                .isInstanceOf(AccessDeniedException.class);
        verify(contracts, never()).findByIdForUpdate(any());
        verify(tenants, never()).clearRepresentative(any());
        verify(tenants, never()).save(any());
    }

    @Test
    void changeRepresentative_buildingScopeDependencyFails_throws503AndDoesNotMutate() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        doThrow(new BusinessException("DEPENDENCY_UNAVAILABLE", "Khong kiem tra duoc phan cong toa nha",
                HttpStatus.SERVICE_UNAVAILABLE))
                .when(buildingScope).assertAssigned(contract.getBuildingId());

        assertThatThrownBy(() -> service.changeRepresentative(9L, 32L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("DEPENDENCY_UNAVAILABLE"));
        verify(contracts, never()).findByIdForUpdate(any());
        verify(tenants, never()).clearRepresentative(any());
        verify(tenants, never()).save(any());
    }

    @Test
    void addTenant_unassignedBuilding_throwsAccessDeniedAndDoesNotMutate() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        doThrow(new AccessDeniedException("Staff chua duoc phan cong tai toa nha nay"))
                .when(buildingScope).assertAssigned(contract.getBuildingId());

        assertThatThrownBy(() -> service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 12, 1))))
                .isInstanceOf(AccessDeniedException.class);
        verify(contracts, never()).lockNamedKey(anyString());
        verify(contracts, never()).findByIdForUpdate(any());
        verify(tenants, never()).save(any());
    }

    @Test
    void addTenant_buildingScopeDependencyFails_throws503AndDoesNotMutate() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        doThrow(new BusinessException("DEPENDENCY_UNAVAILABLE", "Khong kiem tra duoc phan cong toa nha",
                HttpStatus.SERVICE_UNAVAILABLE))
                .when(buildingScope).assertAssigned(contract.getBuildingId());

        assertThatThrownBy(() -> service.addTenant(9L, addRequest(88L, LocalDate.of(2026, 12, 1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("DEPENDENCY_UNAVAILABLE"));
        verify(contracts, never()).lockNamedKey(anyString());
        verify(contracts, never()).findByIdForUpdate(any());
        verify(tenants, never()).save(any());
    }

    // ---- Task 5C: Tenant Leave ----

    private static TenantLeaveRequest leaveRequest(LocalDate leftAt) {
        TenantLeaveRequest req = new TenantLeaveRequest();
        req.setLeftAt(leftAt);
        return req;
    }

    @Test
    void leaveTenant_validRequest_setsLeftAtAndPreservesRow() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        ContractTenant departing = tenant(32L, 88L, false, null);
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(tenants.findByIdAndContractId(32L, 9L)).thenReturn(Optional.of(departing));
        when(tenants.countByContractIdAndLeftAtIsNull(9L)).thenReturn(2L);
        when(tenants.save(any(ContractTenant.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate leftDate = LocalDate.of(2026, 12, 1);
        ContractTenantResponse response = service.leaveTenant(9L, 32L, leaveRequest(leftDate));

        assertThat(response.getId()).isEqualTo(32L);
        assertThat(response.getLeftAt()).isEqualTo(leftDate);
        verify(tenants).save(departing);
        verify(tenants, never()).delete(any());
        verify(tenants, never()).deleteById(any());
    }

    /** Spec 10: a successful leave emits TenantRemoved with contract/room/building/customer/leftAt. */
    @Test
    void leaveTenant_success_publishesTenantRemovedWithTheSpecPayload() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        ContractTenant departing = tenant(32L, 88L, false, null);
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(tenants.findByIdAndContractId(32L, 9L)).thenReturn(Optional.of(departing));
        when(tenants.countByContractIdAndLeftAtIsNull(9L)).thenReturn(2L);
        when(tenants.save(any(ContractTenant.class))).thenAnswer(inv -> inv.getArgument(0));

        LocalDate leftDate = LocalDate.of(2026, 12, 1);
        service.leaveTenant(9L, 32L, leaveRequest(leftDate));

        ArgumentCaptor<TenantRemovedPayload> payload = ArgumentCaptor.forClass(TenantRemovedPayload.class);
        verify(outbox).publishTenantRemoved(payload.capture());
        assertThat(payload.getValue().getContractId()).isEqualTo(9L);
        assertThat(payload.getValue().getRoomId()).isEqualTo(12L);
        assertThat(payload.getValue().getBuildingId()).isEqualTo(3L);
        assertThat(payload.getValue().getCustomerId()).isEqualTo(88L);
        assertThat(payload.getValue().getLeftAt()).isEqualTo(leftDate);
    }

    /** The event lives in the same transaction as left_at, so a rejected leave publishes nothing. */
    @Test
    void leaveTenant_rejectedByAnyRule_publishesNoTenantRemoved() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        ContractTenant representative = tenant(31L, 77L, true, null);
        ContractTenant lastOccupant = tenant(32L, 88L, false, null);
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(tenants.findByIdAndContractId(31L, 9L)).thenReturn(Optional.of(representative));
        when(tenants.findByIdAndContractId(32L, 9L)).thenReturn(Optional.of(lastOccupant));
        when(tenants.countByContractIdAndLeftAtIsNull(9L)).thenReturn(1L);

        assertThatThrownBy(() -> service.leaveTenant(9L, 31L, leaveRequest(LocalDate.of(2026, 12, 1))))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.leaveTenant(9L, 32L, leaveRequest(LocalDate.of(2026, 12, 1))))
                .isInstanceOf(BusinessException.class);

        verify(tenants, never()).save(any());
        verify(outbox, never()).publishTenantRemoved(any());
    }

    @Test
    void leaveTenant_representativeCannotLeave_throwsCannotRemoveRepresentative() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        ContractTenant rep = tenant(31L, 77L, true, null);
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(tenants.findByIdAndContractId(31L, 9L)).thenReturn(Optional.of(rep));

        assertThatThrownBy(() -> service.leaveTenant(9L, 31L, leaveRequest(LocalDate.of(2026, 12, 1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("CANNOT_REMOVE_REPRESENTATIVE"));
        verify(tenants, never()).save(any());
    }

    @Test
    void leaveTenant_lastActiveTenantInActiveContract_throwsLastTenant() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        ContractTenant nonRep = tenant(32L, 88L, false, null);
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(tenants.findByIdAndContractId(32L, 9L)).thenReturn(Optional.of(nonRep));
        when(tenants.countByContractIdAndLeftAtIsNull(9L)).thenReturn(1L);

        assertThatThrownBy(() -> service.leaveTenant(9L, 32L, leaveRequest(LocalDate.of(2026, 12, 1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("LAST_TENANT"));
        verify(tenants, never()).save(any());
    }

    @Test
    void leaveTenant_alreadyLeft_throwsTenantNotActive() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        ContractTenant alreadyLeft = tenant(32L, 88L, false, LocalDate.of(2026, 11, 20));
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(tenants.findByIdAndContractId(32L, 9L)).thenReturn(Optional.of(alreadyLeft));

        assertThatThrownBy(() -> service.leaveTenant(9L, 32L, leaveRequest(LocalDate.of(2026, 12, 1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("TENANT_NOT_ACTIVE"));
        verify(tenants, never()).save(any());
    }

    @Test
    void leaveTenant_leftAtBeforeJoinedAt_throwsInvalidDate() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        ContractTenant departing = tenant(32L, 88L, false, null);
        departing.setJoinedAt(LocalDate.of(2026, 11, 10));
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(tenants.findByIdAndContractId(32L, 9L)).thenReturn(Optional.of(departing));

        assertThatThrownBy(() -> service.leaveTenant(9L, 32L, leaveRequest(LocalDate.of(2026, 11, 9))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("INVALID_DATE"));
        verify(tenants, never()).save(any());
    }

    @Test
    void leaveTenant_missingContract_throwsNotFound() {
        when(contracts.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.leaveTenant(9L, 32L, leaveRequest(LocalDate.of(2026, 12, 1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("CONTRACT_NOT_FOUND"));
        verify(tenants, never()).save(any());
    }

    @Test
    void leaveTenant_tenantOfAnotherContract_throwsTenantNotFound() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(tenants.findByIdAndContractId(32L, 9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.leaveTenant(9L, 32L, leaveRequest(LocalDate.of(2026, 12, 1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("TENANT_NOT_FOUND"));
        verify(tenants, never()).save(any());
    }

    @Test
    void leaveTenant_nullRequestOrDate_throwsValidationError() {
        assertThatThrownBy(() -> service.leaveTenant(9L, 32L, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("VALIDATION_ERROR"));

        TenantLeaveRequest nullDateReq = new TenantLeaveRequest();
        assertThatThrownBy(() -> service.leaveTenant(9L, 32L, nullDateReq))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("VALIDATION_ERROR"));
        verifyNoInteractions(contracts, tenants);
    }

    @Test
    void leaveTenant_assignedBuilding_allowsLeave() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        ContractTenant departing = tenant(32L, 88L, false, null);
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(tenants.findByIdAndContractId(32L, 9L)).thenReturn(Optional.of(departing));
        when(tenants.countByContractIdAndLeftAtIsNull(9L)).thenReturn(2L);
        when(tenants.save(any(ContractTenant.class))).thenReturn(departing);

        service.leaveTenant(9L, 32L, leaveRequest(LocalDate.of(2026, 12, 1)));

        verify(buildingScope).assertAssigned(contract.getBuildingId());
        verify(tenants).save(departing);
    }

    @Test
    void leaveTenant_unassignedBuilding_throwsAccessDeniedAndDoesNotMutate() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        doThrow(new AccessDeniedException("Staff chua duoc phan cong tai toa nha nay"))
                .when(buildingScope).assertAssigned(contract.getBuildingId());

        assertThatThrownBy(() -> service.leaveTenant(9L, 32L, leaveRequest(LocalDate.of(2026, 12, 1))))
                .isInstanceOf(AccessDeniedException.class);
        verify(contracts, never()).findByIdForUpdate(any());
        verify(tenants, never()).save(any());
    }

    @Test
    void leaveTenant_buildingScopeDependencyFails_throws503AndDoesNotMutate() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        doThrow(new BusinessException("DEPENDENCY_UNAVAILABLE", "Khong kiem tra duoc phan cong toa nha",
                HttpStatus.SERVICE_UNAVAILABLE))
                .when(buildingScope).assertAssigned(contract.getBuildingId());

        assertThatThrownBy(() -> service.leaveTenant(9L, 32L, leaveRequest(LocalDate.of(2026, 12, 1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("DEPENDENCY_UNAVAILABLE"));
        verify(contracts, never()).findByIdForUpdate(any());
        verify(tenants, never()).save(any());
    }

    @Test
    void renewContract_validActiveContract_updatesEndDateAndPublishesEvent() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setEndDate(LocalDate.of(2027, 5, 1));
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(contracts.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tenants.findByContractIdAndLeftAtIsNullOrderByJoinedAtAsc(9L))
                .thenReturn(List.of(tenant(31L, 77L, true, null)));

        com.rental.contracttenant.dto.RenewContractRequest req =
                new com.rental.contracttenant.dto.RenewContractRequest(LocalDate.of(2027, 11, 1));
        ContractResponse response = service.renewContract(9L, req);

        assertThat(response.getEndDate()).isEqualTo(LocalDate.of(2027, 11, 1));
        verify(outbox).publishContractRenewed(any());
    }

    /**
     * Spec 9.4: the terminate API only moves end_date and records the reason. The contract stays
     * ACTIVE until the daily job sees the date pass, so occupants keep their left_at empty and no
     * ContractEnded goes out early.
     */
    @Test
    void terminateContract_futureDate_keepsActiveAndClosesNothing() {
        Contract contract = activeContractEnding(LocalDate.of(2027, 5, 1));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(contracts.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

        ContractResponse response = service.terminateContract(9L, terminateRequest(LocalDate.of(2027, 1, 1)));

        assertThat(response.getStatus()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(response.getEndDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(response.getTerminationReason()).isEqualTo("Ket thuc som");
        verify(contracts).save(contract);
        verify(tenants, never()).save(any());
        verify(moveOutRequests, never()).save(any());
        verify(moveOutRequests, never()).saveAndFlush(any());
        verify(outbox, never()).publishContractEnded(any());
    }

    @Test
    void terminateContract_terminationDateEqualsStartDate_isAllowed() {
        Contract contract = activeContractEnding(LocalDate.of(2027, 5, 1));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(contracts.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

        ContractResponse response = service.terminateContract(9L, terminateRequest(contract.getStartDate()));

        assertThat(response.getEndDate()).isEqualTo(contract.getStartDate());
    }

    @Test
    void terminateContract_terminationDateEqualsEndDate_isAllowed() {
        Contract contract = activeContractEnding(LocalDate.of(2027, 5, 1));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(contracts.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));

        ContractResponse response = service.terminateContract(9L, terminateRequest(contract.getEndDate()));

        assertThat(response.getEndDate()).isEqualTo(LocalDate.of(2027, 5, 1));
    }

    @Test
    void terminateContract_terminationDateBeforeStartDate_isRejectedWithoutWriting() {
        Contract contract = activeContractEnding(LocalDate.of(2027, 5, 1));
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.terminateContract(9L, terminateRequest(contract.getStartDate().minusDays(1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_DATE"));
        verify(contracts, never()).findByIdForUpdate(any());
        verify(contracts, never()).save(any());
        verify(outbox, never()).publishContractEnded(any());
    }

    @Test
    void terminateContract_terminationDateAfterCurrentEndDate_isRejectedWithoutWriting() {
        Contract contract = activeContractEnding(LocalDate.of(2027, 5, 1));
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.terminateContract(9L, terminateRequest(LocalDate.of(2027, 5, 2))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_DATE"));
        verify(contracts, never()).findByIdForUpdate(any());
        verify(contracts, never()).save(any());
    }

    @Test
    void terminateContract_changedWhileWaitingForTheLock_isRevalidated() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setStartDate(LocalDate.of(2026, 11, 1));
        contract.setEndDate(LocalDate.of(2027, 5, 1));
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));

        Contract locked = fixture();
        locked.setStatus(ContractStatus.ACTIVE);
        locked.setStartDate(LocalDate.of(2026, 11, 1));
        locked.setEndDate(LocalDate.of(2026, 12, 31));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> service.terminateContract(9L, terminateRequest(LocalDate.of(2027, 1, 1))))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_DATE"));
        verify(contracts, never()).save(any());
    }

    // ---- move-out: building scope on the listing -------------------------------------------

    @Test
    void searchMoveOutRequests_staffWithoutBuildingId_isRestrictedToAssignedBuildings() {
        when(buildingScope.getAssignedBuildingIds()).thenReturn(List.of(3L, 4L));
        Pageable pageable = PageRequest.of(0, 20);
        when(moveOutRequests.findByFilters(null, List.of(3L, 4L), null, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchMoveOutRequests(null, null, pageable);

        verify(moveOutRequests).findByFilters(null, List.of(3L, 4L), null, pageable);
        verify(buildingScope, never()).assertAssigned(any());
    }

    @Test
    void searchMoveOutRequests_admin_isNotRestricted() {
        when(buildingScope.getAssignedBuildingIds()).thenReturn(null);
        Pageable pageable = PageRequest.of(0, 20);
        when(moveOutRequests.findByFilters(null, null, null, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchMoveOutRequests(null, null, pageable);

        verify(moveOutRequests).findByFilters(null, null, null, pageable);
    }

    @Test
    void searchMoveOutRequests_unassignedBuildingId_isRejectedByTheScopeCheck() {
        doThrow(new AccessDeniedException("Staff chua duoc phan cong tai toa nha nay"))
                .when(buildingScope).assertAssigned(99L);

        assertThatThrownBy(() -> service.searchMoveOutRequests(99L, null, PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        verify(moveOutRequests, never()).findByFilters(any(), any(), any(), any());
    }

    // ---- move-out: concurrency --------------------------------------------------------------

    @Test
    void createMoveOutRequest_secondPendingForSameContract_conflictsBeforeInserting() {
        Contract contract = activeContractEnding(LocalDate.of(2027, 5, 1));
        signInAs("77", List.of("CUSTOMER"));
        when(tenants.findByContractIdAndCustomerIdAndLeftAtIsNull(9L, 77L))
                .thenReturn(Optional.of(tenant(31L, 77L, true, null)));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(moveOutRequests.existsByContractIdAndStatus(9L, MoveOutStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.createMoveOutRequest(
                new com.rental.contracttenant.dto.CreateMoveOutRequest(9L, LocalDate.of(2026, 12, 1), "Ve que")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("REQUEST_ALREADY_EXISTS"));
        verify(moveOutRequests, never()).saveAndFlush(any());
        verify(outbox, never()).publishMoveOutRequested(any());
    }

    /** Spec 9.4 reason is optional; the NOT NULL reason column gets "" rather than failing. */
    @Test
    void createMoveOutRequest_withoutReason_storesEmptyStringAndPublishes() {
        Contract contract = activeContractEnding(LocalDate.of(2027, 5, 1));
        signInAs("77", List.of("CUSTOMER"));
        when(tenants.findByContractIdAndCustomerIdAndLeftAtIsNull(9L, 77L))
                .thenReturn(Optional.of(tenant(31L, 77L, true, null)));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(moveOutRequests.existsByContractIdAndStatus(9L, MoveOutStatus.PENDING)).thenReturn(false);
        ArgumentCaptor<MoveOutRequest> saved = ArgumentCaptor.forClass(MoveOutRequest.class);
        when(moveOutRequests.saveAndFlush(saved.capture())).thenAnswer(inv -> {
            MoveOutRequest mor = inv.getArgument(0);
            mor.setId(5L);
            return mor;
        });

        var response = service.createMoveOutRequest(
                new com.rental.contracttenant.dto.CreateMoveOutRequest(9L, LocalDate.of(2026, 12, 1), null));

        assertThat(saved.getValue().getReason()).isEmpty();
        assertThat(response.getReason()).isEmpty();
        verify(outbox).publishMoveOutRequested(any());
    }

    @Test
    void createMoveOutRequest_lostRaceOnThePartialIndex_mapsToTheSameConflict() {
        Contract contract = activeContractEnding(LocalDate.of(2027, 5, 1));
        signInAs("77", List.of("CUSTOMER"));
        when(tenants.findByContractIdAndCustomerIdAndLeftAtIsNull(9L, 77L))
                .thenReturn(Optional.of(tenant(31L, 77L, true, null)));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(moveOutRequests.existsByContractIdAndStatus(9L, MoveOutStatus.PENDING)).thenReturn(false);
        when(moveOutRequests.saveAndFlush(any(MoveOutRequest.class))).thenThrow(
                new DataIntegrityViolationException("duplicate key",
                        new org.hibernate.exception.ConstraintViolationException("boom", new java.sql.SQLException(),
                                "uq_move_out_request_pending")));

        assertThatThrownBy(() -> service.createMoveOutRequest(
                new com.rental.contracttenant.dto.CreateMoveOutRequest(9L, LocalDate.of(2026, 12, 1), "Ve que")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("REQUEST_ALREADY_EXISTS"));
        verify(outbox, never()).publishMoveOutRequested(any());
    }

    @Test
    void approveMoveOutRequest_requestAlreadyHandled_isRejectedWithoutSecondEvent() {
        Contract contract = activeContractEnding(LocalDate.of(2027, 5, 1));
        MoveOutRequest alreadyApproved = moveOut(5L, MoveOutStatus.APPROVED);
        when(moveOutRequests.findById(5L)).thenReturn(Optional.of(alreadyApproved));
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(moveOutRequests.findByIdForUpdate(5L)).thenReturn(Optional.of(alreadyApproved));

        assertThatThrownBy(() -> service.approveMoveOutRequest(5L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_STATUS"));
        verify(contracts, never()).save(any());
        verify(moveOutRequests, never()).save(any());
        verify(outbox, never()).publishMoveOutApproved(any());
    }

    @Test
    void approveMoveOutRequest_thenRejectRace_secondSeesApprovedAndWritesNothing() {
        Contract contract = activeContractEnding(LocalDate.of(2027, 5, 1));
        MoveOutRequest approved = moveOut(5L, MoveOutStatus.APPROVED);
        when(moveOutRequests.findById(5L)).thenReturn(Optional.of(approved));
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(moveOutRequests.findByIdForUpdate(5L)).thenReturn(Optional.of(approved));

        assertThatThrownBy(() -> service.rejectMoveOutRequest(5L,
                new com.rental.contracttenant.dto.RejectMoveOutRequest("Khach doi y")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_STATUS"));
        verify(moveOutRequests, never()).save(any());
    }

    @Test
    void approveMoveOutRequest_pending_updatesEndDateFromTheLockedRowAndPublishesOnce() {
        Contract contract = activeContractEnding(LocalDate.of(2027, 5, 1));
        MoveOutRequest pending = moveOut(5L, MoveOutStatus.PENDING);
        when(moveOutRequests.findById(5L)).thenReturn(Optional.of(pending));
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(moveOutRequests.findByIdForUpdate(5L)).thenReturn(Optional.of(pending));
        when(contracts.save(any(Contract.class))).thenAnswer(inv -> inv.getArgument(0));
        when(moveOutRequests.save(any(MoveOutRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.approveMoveOutRequest(5L);

        assertThat(response.getStatus()).isEqualTo(MoveOutStatus.APPROVED);
        assertThat(contract.getEndDate()).isEqualTo(pending.getExpectedMoveOutDate());
        verify(outbox, times(1)).publishMoveOutApproved(any());
    }

    // ---- move-out: re-validation after the lock -------------------------------------------------

    /** The occupant row is read before the lock; a departure in between must stop the insert. */
    @Test
    void createMoveOutRequest_occupantLeftWhileWaitingForTheLock_isRejected() {
        Contract contract = activeContractEnding(LocalDate.of(2027, 5, 1));
        signInAs("77", List.of("CUSTOMER"));
        when(tenants.findByContractIdAndCustomerIdAndLeftAtIsNull(9L, 77L))
                .thenReturn(Optional.of(tenant(31L, 77L, true, null)), Optional.empty());
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.createMoveOutRequest(
                new com.rental.contracttenant.dto.CreateMoveOutRequest(9L, LocalDate.of(2026, 12, 1), "Ve que")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("NOT_REPRESENTATIVE"));
        verify(moveOutRequests, never()).saveAndFlush(any());
        verify(outbox, never()).publishMoveOutRequested(any());
    }

    /** A concurrent representative swap must be seen too, even though the requester still occupies. */
    @Test
    void createMoveOutRequest_noLongerRepresentativeAfterTheLock_isRejected() {
        Contract contract = activeContractEnding(LocalDate.of(2027, 5, 1));
        signInAs("77", List.of("CUSTOMER"));
        when(tenants.findByContractIdAndCustomerIdAndLeftAtIsNull(9L, 77L))
                .thenReturn(Optional.of(tenant(31L, 77L, true, null)), Optional.of(tenant(31L, 77L, false, null)));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));

        assertThatThrownBy(() -> service.createMoveOutRequest(
                new com.rental.contracttenant.dto.CreateMoveOutRequest(9L, LocalDate.of(2026, 12, 1), "Ve que")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("NOT_REPRESENTATIVE"));
        verify(moveOutRequests, never()).saveAndFlush(any());
    }

    /**
     * The date was valid against the pre-lock read; a terminate/renew that shortened end_date to before
     * it must be re-checked, otherwise the request would carry a date outside the contract.
     */
    @Test
    void createMoveOutRequest_contractShortenedWhileWaitingForTheLock_isRejected() {
        activeContractEnding(LocalDate.of(2027, 5, 1));
        Contract shortened = fixture();
        shortened.setStatus(ContractStatus.ACTIVE);
        shortened.setStartDate(LocalDate.of(2026, 11, 1));
        shortened.setEndDate(LocalDate.of(2026, 11, 30));
        signInAs("77", List.of("CUSTOMER"));
        when(tenants.findByContractIdAndCustomerIdAndLeftAtIsNull(9L, 77L))
                .thenReturn(Optional.of(tenant(31L, 77L, true, null)));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(shortened));

        assertThatThrownBy(() -> service.createMoveOutRequest(
                new com.rental.contracttenant.dto.CreateMoveOutRequest(9L, LocalDate.of(2026, 12, 1), "Ve que")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_DATE"));
        verify(moveOutRequests, never()).saveAndFlush(any());
        verify(outbox, never()).publishMoveOutRequested(any());
    }

    /** Approving writes expected_move_out_date into end_date, so it must fit the locked contract. */
    @Test
    void approveMoveOutRequest_expectedDatePastTheCurrentEndDate_isRejectedWithoutWriting() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setStartDate(LocalDate.of(2026, 11, 1));
        contract.setEndDate(LocalDate.of(2026, 11, 15));
        MoveOutRequest pending = moveOut(5L, MoveOutStatus.PENDING);
        when(moveOutRequests.findById(5L)).thenReturn(Optional.of(pending));
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(moveOutRequests.findByIdForUpdate(5L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.approveMoveOutRequest(5L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_DATE"));
        assertThat(pending.getStatus()).isEqualTo(MoveOutStatus.PENDING);
        assertThat(contract.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 15));
        verify(contracts, never()).save(any());
        verify(moveOutRequests, never()).save(any());
        verify(outbox, never()).publishMoveOutApproved(any());
    }

    /** A contract whose start_date moved past the requested date is equally invalid to approve. */
    @Test
    void approveMoveOutRequest_expectedDateBeforeTheCurrentStartDate_isRejectedWithoutWriting() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setStartDate(LocalDate.of(2027, 1, 1));
        contract.setEndDate(LocalDate.of(2027, 5, 1));
        MoveOutRequest pending = moveOut(5L, MoveOutStatus.PENDING);
        when(moveOutRequests.findById(5L)).thenReturn(Optional.of(pending));
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(moveOutRequests.findByIdForUpdate(5L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.approveMoveOutRequest(5L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_DATE"));
        verify(contracts, never()).save(any());
        verify(moveOutRequests, never()).save(any());
    }

    @Test
    void processRoommateAccepted_capacityExceeded_publishesRejected() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        when(contracts.findFirstByRoomIdAndStatusOrderByStartDateDesc(12L, ContractStatus.ACTIVE))
                .thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(properties.getRoom(12L)).thenReturn(new PropertyClient.RoomInfo(12L, 3L, 1));
        when(tenants.countByContractIdAndLeftAtIsNull(9L)).thenReturn(1L);

        com.rental.contracttenant.dto.RoommateApplicationAcceptedEvent event =
                new com.rental.contracttenant.dto.RoommateApplicationAcceptedEvent(10L, 20L, 12L, 3L, 77L, 88L);
        service.processRoommateAccepted(event);

        verify(outbox).publishTenantAddRejected(any());
        verify(outbox, never()).publishTenantAdded(any());
    }

    @Test
    void processRoommateAccepted_success_savesTenantAndPublishesAdded() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        when(contracts.findFirstByRoomIdAndStatusOrderByStartDateDesc(12L, ContractStatus.ACTIVE))
                .thenReturn(Optional.of(contract));
        when(contracts.findByIdForUpdate(9L)).thenReturn(Optional.of(contract));
        when(properties.getRoom(12L)).thenReturn(new PropertyClient.RoomInfo(12L, 3L, 2));
        when(tenants.countByContractIdAndLeftAtIsNull(9L)).thenReturn(1L);

        com.rental.contracttenant.dto.RoommateApplicationAcceptedEvent event =
                new com.rental.contracttenant.dto.RoommateApplicationAcceptedEvent(10L, 20L, 12L, 3L, 77L, 88L);
        service.processRoommateAccepted(event);

        verify(tenants).save(any(ContractTenant.class));
        verify(outbox).publishTenantAdded(any());
    }

    @Test
    void getBillingData_queriesEffectiveContracts() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setEndDate(LocalDate.of(2027, 11, 1));
        when(contracts.findContractsEffectiveInMonth(any(), any(), any()))
                .thenReturn(List.of(contract));
        when(tenants.findByContractIdOrderByJoinedAtAsc(9L))
                .thenReturn(List.of(tenant(31L, 77L, true, null)));

        var data = service.getBillingData(10, 2026);
        assertThat(data).hasSize(1);
        assertThat(data.get(0).getContractId()).isEqualTo(9L);
        assertThat(data.get(0).getTenants()).hasSize(1);
    }

    @Test
    void checkRoomTenant_existingOccupant_returnsTrue() {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        when(contracts.findFirstByRoomIdAndStatusOrderByStartDateDesc(12L, ContractStatus.ACTIVE))
                .thenReturn(Optional.of(contract));
        when(tenants.existsByContractIdAndCustomerIdAndLeftAtIsNull(9L, 77L)).thenReturn(true);

        var res = service.checkRoomTenant(12L, 77L);
        assertThat(res.isTenant()).isTrue();
        assertThat(res.getContractId()).isEqualTo(9L);
    }

    private Contract activeContractEnding(LocalDate endDate) {
        Contract contract = fixture();
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setStartDate(LocalDate.of(2026, 11, 1));
        contract.setEndDate(endDate);
        when(contracts.findById(9L)).thenReturn(Optional.of(contract));
        return contract;
    }

    private static com.rental.contracttenant.dto.TerminateContractRequest terminateRequest(LocalDate date) {
        return new com.rental.contracttenant.dto.TerminateContractRequest(date, "Ket thuc som");
    }

    private static MoveOutRequest moveOut(Long id, MoveOutStatus status) {
        MoveOutRequest mor = new MoveOutRequest();
        mor.setId(id);
        mor.setContractId(9L);
        mor.setRequestedBy(77L);
        mor.setRequestDate(LocalDate.of(2026, 10, 22));
        mor.setExpectedMoveOutDate(LocalDate.of(2026, 12, 1));
        mor.setReason("Ve que");
        mor.setStatus(status);
        return mor;
    }

    private static void signInAs(String userId, List<String> roles) {
        HeaderUser user = new HeaderUser(userId, "CUSTOMER", roles);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null,
                roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()));
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
