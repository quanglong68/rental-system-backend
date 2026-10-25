package com.rental.contracttenant.service;

import com.rental.common.error.BusinessException;
import com.rental.common.error.NotFoundException;
import com.rental.common.security.HeaderUser;
import com.rental.contracttenant.client.PropertyClient;
import com.rental.contracttenant.dto.AddTenantRequest;
import com.rental.contracttenant.dto.BillingContractData;
import com.rental.contracttenant.dto.ContractDetailResponse;
import com.rental.contracttenant.dto.ContractResponse;
import com.rental.contracttenant.dto.ContractTenantResponse;
import com.rental.contracttenant.dto.CreateDraftContractRequest;
import com.rental.contracttenant.dto.CreateMoveOutRequest;
import com.rental.contracttenant.dto.MoveOutRequestResponse;
import com.rental.contracttenant.dto.RejectMoveOutRequest;
import com.rental.contracttenant.dto.RenewContractRequest;
import com.rental.contracttenant.dto.RoomTenantCheckResponse;
import com.rental.contracttenant.dto.TenantLeaveRequest;
import com.rental.contracttenant.dto.TerminateContractRequest;
import com.rental.contracttenant.dto.UpdateDraftContractRequest;
import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.entity.ContractTenant;
import com.rental.contracttenant.entity.MoveOutRequest;
import com.rental.contracttenant.entity.MoveOutStatus;
import com.rental.contracttenant.outbox.ContractEndedPayload;
import com.rental.contracttenant.outbox.ContractRenewedPayload;
import com.rental.contracttenant.outbox.MoveOutApprovedPayload;
import com.rental.contracttenant.outbox.MoveOutRequestedPayload;
import com.rental.contracttenant.outbox.OutboxEventPublisher;
import com.rental.contracttenant.outbox.TenantRemovedPayload;
import com.rental.contracttenant.repository.ContractRepository;
import com.rental.contracttenant.repository.ContractTenantRepository;
import com.rental.contracttenant.repository.MoveOutRequestRepository;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ContractService {

    private final ContractRepository contracts;
    private final ContractTenantRepository tenants;
    private final MoveOutRequestRepository moveOutRequests;
    private final OutboxEventPublisher outbox;
    private final PropertyClient properties;
    private final BuildingScope buildingScope;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

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

    public ContractResponse updateDraftDetails(Long id, UpdateDraftContractRequest request) {
        if (request == null) {
            throw new BusinessException("VALIDATION_ERROR", "Request khong duoc rong", HttpStatus.BAD_REQUEST);
        }
        if (request.getStartDate() == null) {
            throw new BusinessException("VALIDATION_ERROR", "Ngay bat dau khong duoc rong", HttpStatus.BAD_REQUEST);
        }
        if (request.getEndDate() != null && !request.getEndDate().isAfter(request.getStartDate())) {
            throw new BusinessException("INVALID_DATE", "Ngay ket thuc phai sau ngay bat dau",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);

        return template.execute(status -> {
            Contract contract = contracts.findById(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong " + id));

            buildingScope.assertAssigned(contract.getBuildingId());

            Contract locked = contracts.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong " + id));

            if (locked.getStatus() != ContractStatus.DRAFT) {
                throw new BusinessException("INVALID_STATUS", "Chi sua hop dong o trang thai DRAFT",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            applyDetails(locked, request);
            return ContractResponse.from(contracts.save(locked));
        });
    }

    private static void applyDetails(Contract contract, UpdateDraftContractRequest request) {
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setRentAmount(normalizeMoney(request.getRentAmount()));
        contract.setDepositAmount(request.getDepositAmount() == null ? null
                : normalizeMoney(request.getDepositAmount()));
    }

    public ContractResponse sign(Long id) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        return template.execute(status -> {
            Contract contract = contracts.findById(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong " + id));

            buildingScope.assertAssigned(contract.getBuildingId());

            if (contract.getStatus() != ContractStatus.DRAFT) {
                throw new BusinessException("INVALID_STATUS", "Chi ky hop dong o trang thai DRAFT",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            validateSigningFields(contract);

            contracts.lockNamedKey(signKey("customer", contract.getCustomerId()));
            contracts.lockNamedKey(signKey("room", contract.getRoomId()));

            Contract locked = contracts.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong " + id));
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
                throw new BusinessException("CUSTOMER_ALREADY_IN_ROOM",
                        "Khach da thuoc mot hop dong ACTIVE khac", HttpStatus.CONFLICT);
            }

            locked.setStatus(ContractStatus.ACTIVE);
            Contract saved = contracts.save(locked);

            try {
                tenants.save(initialRepresentative(saved));
            } catch (DataIntegrityViolationException race) {
                if (violates(race, DUPLICATE_CUSTOMER_INDEX) || violates(race, REPRESENTATIVE_INDEX)) {
                    throw new BusinessException("CUSTOMER_ALREADY_IN_ROOM",
                            "Khach da thuoc mot hop dong ACTIVE khac", HttpStatus.CONFLICT);
                }
                throw race;
            }

            outbox.publishContractSigned(saved, List.of(saved.getCustomerId()));

            return ContractResponse.from(saved);
        });
    }

    public ContractTenantResponse addTenant(Long contractId, AddTenantRequest request) {
        if (request == null) {
            throw new BusinessException("VALIDATION_ERROR", "Request khong duoc rong", HttpStatus.BAD_REQUEST);
        }
        if (request.getCustomerId() == null || request.getJoinedAt() == null) {
            throw new BusinessException("VALIDATION_ERROR", "Thieu customerId hoac joinedAt",
                    HttpStatus.BAD_REQUEST);
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);

        return template.execute(status -> {
            Contract contract = contracts.findById(contractId)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND",
                            "Khong tim thay hop dong " + contractId));

            buildingScope.assertAssigned(contract.getBuildingId());

            if (contract.getStatus() != ContractStatus.ACTIVE) {
                throw new BusinessException("INVALID_STATUS", "Chi them nguoi o vao hop dong ACTIVE",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (request.getJoinedAt().isBefore(contract.getStartDate())) {
                throw new BusinessException("INVALID_DATE", "Ngay vao o truoc ngay bat dau hop dong",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            contracts.lockNamedKey(signKey("customer", request.getCustomerId()));
            contracts.lockNamedKey(signKey("room", contract.getRoomId()));

            Contract locked = contracts.findByIdForUpdate(contractId)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND",
                            "Khong tim thay hop dong " + contractId));
            if (!java.util.Objects.equals(locked.getRoomId(), contract.getRoomId())
                    || !java.util.Objects.equals(locked.getCustomerId(), contract.getCustomerId())) {
                throw new IllegalStateException("Hop dong " + contractId
                        + " doi phong hoac khach giua hai lan doc; khong the dam bao thu tu khoa");
            }
            if (locked.getStatus() != ContractStatus.ACTIVE) {
                throw new BusinessException("INVALID_STATUS", "Chi them nguoi o vao hop dong ACTIVE",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            if (tenants.existsByContractIdAndCustomerIdAndLeftAtIsNull(contractId, request.getCustomerId())) {
                throw new BusinessException("CUSTOMER_ALREADY_IN_ROOM",
                        "Khach da o trong hop dong nay", HttpStatus.CONFLICT);
            }
            if (tenants.existsCurrentTenantInContractWithStatus(request.getCustomerId(), ContractStatus.ACTIVE)) {
                throw new BusinessException("CUSTOMER_ALREADY_IN_ROOM",
                        "Khach da thuoc mot hop dong ACTIVE khac", HttpStatus.CONFLICT);
            }

            Integer maxCapacity;
            try {
                PropertyClient.RoomInfo room = properties.getRoom(locked.getRoomId());
                maxCapacity = room == null ? null : room.maxCapacity();
            } catch (BusinessException | CallNotPermittedException | RetryableException unavailable) {
                throw new BusinessException("DEPENDENCY_UNAVAILABLE",
                        "Khong kiem tra duoc suc chua phong", HttpStatus.SERVICE_UNAVAILABLE);
            }
            if (maxCapacity == null || maxCapacity < 1) {
                throw new BusinessException("DEPENDENCY_UNAVAILABLE",
                        "Khong kiem tra duoc suc chua phong", HttpStatus.SERVICE_UNAVAILABLE);
            }

            long occupancy = tenants.countByContractIdAndLeftAtIsNull(contractId);
            if (occupancy + 1 > maxCapacity) {
                throw new BusinessException("ROOM_CAPACITY_EXCEEDED",
                        "Phong da du so nguoi toi da", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            ContractTenant tenant = new ContractTenant();
            tenant.setContractId(locked.getId());
            tenant.setCustomerId(request.getCustomerId());
            tenant.setJoinedAt(request.getJoinedAt());
            tenant.setLeftAt(null);
            tenant.setRepresentative(false);
            try {
                return ContractTenantResponse.from(tenants.save(tenant));
            } catch (DataIntegrityViolationException race) {
                if (isDuplicateActiveCustomer(race)) {
                    throw new BusinessException("CUSTOMER_ALREADY_IN_ROOM",
                            "Khach da o trong hop dong nay", HttpStatus.CONFLICT);
                }
                throw race;
            }
        });
    }

    public void changeRepresentative(Long contractId, Long tenantId) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        template.executeWithoutResult(status -> {
            Contract contract = contracts.findById(contractId)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND",
                            "Khong tim thay hop dong " + contractId));

            buildingScope.assertAssigned(contract.getBuildingId());

            Contract locked = contracts.findByIdForUpdate(contractId)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND",
                            "Khong tim thay hop dong " + contractId));

            ContractTenant tenant = tenants.findByIdAndContractId(tenantId, contractId)
                    .orElseThrow(() -> new NotFoundException("TENANT_NOT_FOUND",
                            "Khong tim thay nguoi o " + tenantId + " trong hop dong " + contractId));

            if (tenant.getLeftAt() != null) {
                throw new BusinessException("TENANT_NOT_ACTIVE",
                        "Nguoi o da roi khoi hop dong", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            tenants.clearRepresentative(locked.getId());
            ContractTenant target = tenants.findById(tenantId)
                    .orElseThrow(() -> new NotFoundException("TENANT_NOT_FOUND",
                            "Khong tim thay nguoi o " + tenantId));
            target.setRepresentative(true);
            tenants.save(target);
        });
    }

    public ContractTenantResponse leaveTenant(Long contractId, Long tenantId, TenantLeaveRequest request) {
        if (request == null || request.getLeftAt() == null) {
            throw new BusinessException("VALIDATION_ERROR", "Thieu leftAt", HttpStatus.BAD_REQUEST);
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);

        return template.execute(status -> {
            Contract contract = contracts.findById(contractId)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND",
                            "Khong tim thay hop dong " + contractId));

            buildingScope.assertAssigned(contract.getBuildingId());

            Contract locked = contracts.findByIdForUpdate(contractId)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND",
                            "Khong tim thay hop dong " + contractId));

            ContractTenant tenant = tenants.findByIdAndContractId(tenantId, contractId)
                    .orElseThrow(() -> new NotFoundException("TENANT_NOT_FOUND",
                            "Khong tim thay nguoi o " + tenantId + " trong hop dong " + contractId));

            if (tenant.getLeftAt() != null) {
                throw new BusinessException("TENANT_NOT_ACTIVE",
                        "Nguoi o da roi khoi hop dong", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            if (request.getLeftAt().isBefore(tenant.getJoinedAt())) {
                throw new BusinessException("INVALID_DATE",
                        "Ngay roi di truoc ngay bat dau o", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            if (tenant.isRepresentative()) {
                throw new BusinessException("CANNOT_REMOVE_REPRESENTATIVE",
                        "Khong the roi hop dong khi dang la nguoi dai dien", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            long activeOccupants = tenants.countByContractIdAndLeftAtIsNull(locked.getId());
            if (activeOccupants <= 1 && locked.getStatus() == ContractStatus.ACTIVE) {
                throw new BusinessException("LAST_TENANT",
                        "Khong the roi vi la nguoi o cuoi cung trong hop dong ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            tenant.setLeftAt(request.getLeftAt());
            ContractTenant saved = tenants.save(tenant);

            // Spec 10 "TenantRemoved": written to the outbox inside the same transaction as left_at, so
            // a failed leave leaves neither the row change nor the event behind.
            outbox.publishTenantRemoved(new TenantRemovedPayload(
                    locked.getId(), locked.getRoomId(), locked.getBuildingId(),
                    saved.getCustomerId(), saved.getLeftAt()));

            return ContractTenantResponse.from(saved);
        });
    }

    public Page<ContractResponse> searchContracts(Long buildingId, Long roomId, ContractStatus status, Pageable pageable) {
        List<Long> allowedBuildings = buildingScope.getAssignedBuildingIds();
        if (buildingId != null) {
            buildingScope.assertAssigned(buildingId);
        }
        return contracts.findByFilters(buildingId, roomId, status, allowedBuildings, pageable)
                .map(ContractResponse::from);
    }

    public List<ContractResponse> findMyContracts() {
        HeaderUser user = currentUser();
        Long customerId = Long.valueOf(user.getUserId());
        return contracts.findActiveOccupantContracts(customerId).stream()
                .map(ContractResponse::from)
                .toList();
    }

    public ContractDetailResponse getContractById(Long id) {
        Contract contract = contracts.findById(id)
                .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong " + id));

        HeaderUser user = currentUser();
        boolean isStaffOrAdmin = user.getRoles().contains("ADMIN") || user.getRoles().contains("QUAN_LY") || user.getRoles().contains("SALE");

        if (isStaffOrAdmin) {
            buildingScope.assertAssigned(contract.getBuildingId());
        } else {
            Long customerId = Long.valueOf(user.getUserId());
            boolean isOccupant = tenants.findByContractIdOrderByJoinedAtAsc(id).stream()
                    .anyMatch(t -> Objects.equals(t.getCustomerId(), customerId));
            if (!isOccupant) {
                throw new AccessDeniedException("Khach khong thuoc hop dong nay");
            }
        }

        List<ContractTenant> tenantList = tenants.findByContractIdOrderByJoinedAtAsc(id);
        return ContractDetailResponse.from(contract, tenantList);
    }

    public ContractResponse renewContract(Long id, RenewContractRequest request) {
        if (request == null || request.getNewEndDate() == null) {
            throw new BusinessException("VALIDATION_ERROR", "Thieu newEndDate", HttpStatus.BAD_REQUEST);
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            Contract contract = contracts.findById(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong " + id));

            buildingScope.assertAssigned(contract.getBuildingId());

            if (contract.getStatus() != ContractStatus.ACTIVE) {
                throw new BusinessException("INVALID_STATUS", "Chi gia han hop dong ACTIVE",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (contract.getEndDate() != null && !request.getNewEndDate().isAfter(contract.getEndDate())) {
                throw new BusinessException("INVALID_DATE", "newEndDate phai sau endDate hien tai",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            contracts.lockNamedKey(signKey("room", contract.getRoomId()));

            Contract locked = contracts.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong " + id));

            if (locked.getStatus() != ContractStatus.ACTIVE) {
                throw new BusinessException("INVALID_STATUS", "Chi gia han hop dong ACTIVE",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (locked.getEndDate() != null && !request.getNewEndDate().isAfter(locked.getEndDate())) {
                throw new BusinessException("INVALID_DATE", "newEndDate phai sau endDate hien tai",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            if (contracts.existsActiveOverlappingContract(locked.getRoomId(), ContractStatus.ACTIVE,
                    locked.getEndDate().plusDays(1), request.getNewEndDate(), locked.getId())) {
                throw new BusinessException("ROOM_ALREADY_CONTRACTED",
                        "Phong da co hop dong ACTIVE trung khoang thoi gian gia han", HttpStatus.CONFLICT);
            }

            locked.setEndDate(request.getNewEndDate());
            Contract saved = contracts.save(locked);

            List<Long> tenantIds = tenants.findByContractIdAndLeftAtIsNullOrderByJoinedAtAsc(saved.getId()).stream()
                    .map(ContractTenant::getCustomerId)
                    .toList();

            outbox.publishContractRenewed(new ContractRenewedPayload(
                    saved.getId(), saved.getRoomId(), saved.getBuildingId(), saved.getEndDate(), tenantIds
            ));

            return ContractResponse.from(saved);
        });
    }

    public ContractResponse terminateContract(Long id, TerminateContractRequest request) {
        if (request == null || request.getTerminationDate() == null || request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", "Thieu terminationDate hoac reason", HttpStatus.BAD_REQUEST);
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            Contract contract = contracts.findById(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong " + id));

            buildingScope.assertAssigned(contract.getBuildingId());

            if (contract.getStatus() != ContractStatus.ACTIVE) {
                throw new BusinessException("INVALID_STATUS", "Chi cham dut hop dong ACTIVE",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            validateTerminationDate(request.getTerminationDate(), contract);

            contracts.lockNamedKey(signKey("room", contract.getRoomId()));

            Contract locked = contracts.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong " + id));

            if (locked.getStatus() != ContractStatus.ACTIVE) {
                throw new BusinessException("INVALID_STATUS", "Chi cham dut hop dong ACTIVE",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            validateTerminationDate(request.getTerminationDate(), locked);

            // Spec 9.4: "Chấm dứt sớm: QUAN_LY đặt ngày chấm dứt (end_date = ngày đó) kèm lý do" and the
            // daily job is what moves an expired ACTIVE contract to TERMINATED and closes occupants.
            // So the contract stays ACTIVE here: closing tenants now would stamp left_at in the future
            // and emit ContractEnded before the termination date has actually passed.
            locked.setEndDate(request.getTerminationDate());
            locked.setTerminationReason(request.getReason());
            Contract saved = contracts.save(locked);

            return ContractResponse.from(saved);
        });
    }

    private static void validateTerminationDate(LocalDate terminationDate, Contract contract) {
        if (terminationDate.isBefore(contract.getStartDate())) {
            throw new BusinessException("INVALID_DATE", "terminationDate khong duoc truoc startDate",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (contract.getEndDate() != null && terminationDate.isAfter(contract.getEndDate())) {
            throw new BusinessException("INVALID_DATE", "terminationDate khong duoc vuot qua endDate hien tai",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /** Bounds that must hold both when a request is created and when it is approved. */
    private static void validateMoveOutDateAgainstContract(LocalDate expectedMoveOutDate, Contract contract) {
        if (expectedMoveOutDate.isBefore(contract.getStartDate())) {
            throw new BusinessException("INVALID_DATE", "expectedMoveOutDate khong duoc truoc startDate",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (contract.getEndDate() != null && expectedMoveOutDate.isAfter(contract.getEndDate())) {
            throw new BusinessException("INVALID_DATE", "expectedMoveOutDate khong duoc vuot qua endDate hien tai",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private static void validateMoveOutDate(LocalDate expectedMoveOutDate, Contract contract, LocalDate today) {
        if (expectedMoveOutDate.isBefore(today)) {
            throw new BusinessException("INVALID_DATE", "expectedMoveOutDate phai tu hom nay",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        validateMoveOutDateAgainstContract(expectedMoveOutDate, contract);
    }

    public MoveOutRequestResponse createMoveOutRequest(CreateMoveOutRequest request) {
        if (request == null || request.getContractId() == null || request.getExpectedMoveOutDate() == null) {
            throw new BusinessException("VALIDATION_ERROR", "Thieu contractId hoac expectedMoveOutDate", HttpStatus.BAD_REQUEST);
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            HeaderUser user = currentUser();
            Long customerId = Long.valueOf(user.getUserId());

            Contract contract = contracts.findById(request.getContractId())
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong " + request.getContractId()));

            if (contract.getStatus() != ContractStatus.ACTIVE) {
                throw new BusinessException("INVALID_STATUS", "Hop dong khong o trang thai ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            ContractTenant tenant = tenants.findByContractIdAndCustomerIdAndLeftAtIsNull(contract.getId(), customerId)
                    .orElseThrow(() -> new BusinessException("NOT_REPRESENTATIVE", "Khach khong phai nguoi o hien tai", HttpStatus.FORBIDDEN));

            if (!tenant.isRepresentative()) {
                throw new BusinessException("NOT_REPRESENTATIVE", "Chi nguoi dai dien moi duoc gui yeu cau tra phong", HttpStatus.FORBIDDEN);
            }

            LocalDate today = LocalDate.now(clock);
            validateMoveOutDate(request.getExpectedMoveOutDate(), contract, today);

            // Serialise creators on the contract row; the partial unique index in V6 is the last line of
            // defence for "mỗi hợp đồng một yêu cầu PENDING" (spec 9.4).
            contracts.lockNamedKey(signKey("room", contract.getRoomId()));
            Contract locked = contracts.findByIdForUpdate(contract.getId())
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong " + contract.getId()));
            if (locked.getStatus() != ContractStatus.ACTIVE) {
                throw new BusinessException("INVALID_STATUS", "Hop dong khong o trang thai ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            // Both reads happened before the lock, so a concurrent representative swap, a departure, or a
            // renew/terminate that moved end_date could have invalidated them. Re-check against the rows
            // as they are now, still inside the transaction that will insert the request.
            ContractTenant lockedRepresentative = tenants
                    .findByContractIdAndCustomerIdAndLeftAtIsNull(locked.getId(), customerId)
                    .orElseThrow(() -> new BusinessException("NOT_REPRESENTATIVE",
                            "Khach khong phai nguoi o hien tai", HttpStatus.FORBIDDEN));
            if (!lockedRepresentative.isRepresentative()) {
                throw new BusinessException("NOT_REPRESENTATIVE",
                        "Chi nguoi dai dien moi duoc gui yeu cau tra phong", HttpStatus.FORBIDDEN);
            }
            validateMoveOutDate(request.getExpectedMoveOutDate(), locked, today);

            if (moveOutRequests.existsByContractIdAndStatus(locked.getId(), MoveOutStatus.PENDING)) {
                throw new BusinessException("REQUEST_ALREADY_EXISTS", "Hop dong da co yeu cau tra phong cho duyet", HttpStatus.CONFLICT);
            }

            MoveOutRequest mor = new MoveOutRequest();
            mor.setContractId(locked.getId());
            mor.setRequestedBy(customerId);
            mor.setRequestDate(today);
            mor.setExpectedMoveOutDate(request.getExpectedMoveOutDate());
            mor.setReason(request.getReason() == null ? "" : request.getReason());
            mor.setStatus(MoveOutStatus.PENDING);
            MoveOutRequest saved;
            try {
                saved = moveOutRequests.saveAndFlush(mor);
            } catch (DataIntegrityViolationException race) {
                if (violates(race, PENDING_MOVE_OUT_INDEX)) {
                    throw new BusinessException("REQUEST_ALREADY_EXISTS",
                            "Hop dong da co yeu cau tra phong cho duyet", HttpStatus.CONFLICT);
                }
                throw race;
            }

            outbox.publishMoveOutRequested(new MoveOutRequestedPayload(
                    saved.getId(), locked.getId(), locked.getRoomId(), locked.getBuildingId(), saved.getExpectedMoveOutDate()
            ));

            return MoveOutRequestResponse.from(saved);
        });
    }

    public Page<MoveOutRequestResponse> searchMoveOutRequests(Long buildingId, MoveOutStatus status, Pageable pageable) {
        List<Long> allowedBuildings = buildingScope.getAssignedBuildingIds();
        if (buildingId != null) {
            buildingScope.assertAssigned(buildingId);
        }
        return moveOutRequests.findByFilters(buildingId, allowedBuildings, status, pageable)
                .map(MoveOutRequestResponse::from);
    }

    public List<MoveOutRequestResponse> findMyMoveOutRequests() {
        HeaderUser user = currentUser();
        Long customerId = Long.valueOf(user.getUserId());
        return moveOutRequests.findByRequestedByOrderByCreatedAtDesc(customerId).stream()
                .map(MoveOutRequestResponse::from)
                .toList();
    }

    public MoveOutRequestResponse approveMoveOutRequest(Long id) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            MoveOutRequest mor = moveOutRequests.findById(id)
                    .orElseThrow(() -> new NotFoundException("MOVE_OUT_NOT_FOUND", "Khong tim thay yeu cau tra phong " + id));

            Contract contract = contracts.findById(mor.getContractId())
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong"));

            buildingScope.assertAssigned(contract.getBuildingId());

            // Lock order stays room advisory -> contract row -> request row, so terminate/renew (which take
            // room + contract) cannot interleave with approve and rewrite end_date from stale data.
            contracts.lockNamedKey(signKey("room", contract.getRoomId()));
            Contract locked = contracts.findByIdForUpdate(contract.getId())
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong"));
            MoveOutRequest lockedMor = moveOutRequests.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("MOVE_OUT_NOT_FOUND", "Khong tim thay yeu cau tra phong " + id));

            if (lockedMor.getStatus() != MoveOutStatus.PENDING) {
                throw new BusinessException("INVALID_STATUS", "Chi duyet yeu cau o trang thai PENDING", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (locked.getStatus() != ContractStatus.ACTIVE) {
                throw new BusinessException("INVALID_STATUS", "Hop dong khong o trang thai ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            // The request was validated against the contract as it read on creation; by now a renewal or a
            // termination may have moved start_date/end_date. Re-check against the locked row before it
            // becomes the new end_date.
            validateMoveOutDateAgainstContract(lockedMor.getExpectedMoveOutDate(), locked);

            locked.setEndDate(lockedMor.getExpectedMoveOutDate());
            contracts.save(locked);

            lockedMor.setStatus(MoveOutStatus.APPROVED);
            MoveOutRequest savedMor = moveOutRequests.save(lockedMor);

            List<Long> tenantIds = tenants.findByContractIdAndLeftAtIsNullOrderByJoinedAtAsc(locked.getId()).stream()
                    .map(ContractTenant::getCustomerId)
                    .toList();

            outbox.publishMoveOutApproved(new MoveOutApprovedPayload(
                    savedMor.getId(), locked.getId(), locked.getRoomId(), locked.getBuildingId(),
                    savedMor.getExpectedMoveOutDate(), tenantIds
            ));

            return MoveOutRequestResponse.from(savedMor);
        });
    }

    public MoveOutRequestResponse rejectMoveOutRequest(Long id, RejectMoveOutRequest request) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            MoveOutRequest mor = moveOutRequests.findById(id)
                    .orElseThrow(() -> new NotFoundException("MOVE_OUT_NOT_FOUND", "Khong tim thay yeu cau tra phong " + id));

            Contract contract = contracts.findById(mor.getContractId())
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Khong tim thay hop dong"));

            buildingScope.assertAssigned(contract.getBuildingId());

            MoveOutRequest lockedMor = moveOutRequests.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("MOVE_OUT_NOT_FOUND", "Khong tim thay yeu cau tra phong " + id));

            if (lockedMor.getStatus() != MoveOutStatus.PENDING) {
                throw new BusinessException("INVALID_STATUS", "Chi tu choi yeu cau o trang thai PENDING", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            if (request != null && request.getReason() != null && !request.getReason().isBlank()) {
                lockedMor.setReason(request.getReason());
            }
            lockedMor.setStatus(MoveOutStatus.REJECTED);
            MoveOutRequest saved = moveOutRequests.save(lockedMor);

            return MoveOutRequestResponse.from(saved);
        });
    }

    public List<BillingContractData> getBillingData(int month, int year) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        List<Contract> effectiveContracts = contracts.findContractsEffectiveInMonth(
                monthStart, monthEnd, List.of(ContractStatus.ACTIVE, ContractStatus.ENDED, ContractStatus.TERMINATED)
        );

        return effectiveContracts.stream().map(c -> {
            List<ContractTenant> contractTenants = tenants.findByContractIdOrderByJoinedAtAsc(c.getId());
            List<BillingContractData.BillingTenantData> tenantDataList = contractTenants.stream()
                    .map(t -> new BillingContractData.BillingTenantData(
                            t.getCustomerId(), t.getJoinedAt(), t.getLeftAt(), t.isRepresentative()
                    ))
                    .toList();
            return new BillingContractData(
                    c.getId(), c.getRoomId(), c.getBuildingId(), c.getCustomerId(),
                    c.getStatus().name(), c.getStartDate(), c.getEndDate(), c.getRentAmount(), tenantDataList
            );
        }).toList();
    }

    public RoomTenantCheckResponse checkRoomTenant(Long roomId, Long customerId) {
        Optional<Contract> contractOpt = contracts.findFirstByRoomIdAndStatusOrderByStartDateDesc(roomId, ContractStatus.ACTIVE);
        if (contractOpt.isEmpty()) {
            return new RoomTenantCheckResponse(false, null, null);
        }
        Contract contract = contractOpt.get();
        boolean isTenant = tenants.existsByContractIdAndCustomerIdAndLeftAtIsNull(contract.getId(), customerId);
        if (isTenant) {
            return new RoomTenantCheckResponse(true, contract.getId(), contract.getBuildingId());
        }
        return new RoomTenantCheckResponse(false, null, null);
    }

    public void processRoommateAccepted(com.rental.contracttenant.dto.RoommateApplicationAcceptedEvent event) {
        Long roomId = event.getRoomId();
        Long buildingId = event.getBuildingId();
        Long applicationId = event.getApplicationId();
        Long ownerCustomerId = event.getOwnerCustomerId();
        Long applicantCustomerId = event.getApplicantCustomerId();

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            Optional<Contract> activeContractOpt = contracts.findFirstByRoomIdAndStatusOrderByStartDateDesc(roomId, ContractStatus.ACTIVE);
            if (activeContractOpt.isEmpty()) {
                outbox.publishTenantAddRejected(new com.rental.contracttenant.outbox.TenantAddRejectedPayload(
                        roomId, buildingId, applicationId, ownerCustomerId, applicantCustomerId, "NO_ACTIVE_CONTRACT"
                ));
                return;
            }

            Contract contract = activeContractOpt.get();

            contracts.lockNamedKey(signKey("customer", applicantCustomerId));
            contracts.lockNamedKey(signKey("room", contract.getRoomId()));

            Contract locked = contracts.findByIdForUpdate(contract.getId()).orElse(null);
            if (locked == null || locked.getStatus() != ContractStatus.ACTIVE) {
                outbox.publishTenantAddRejected(new com.rental.contracttenant.outbox.TenantAddRejectedPayload(
                        roomId, buildingId, applicationId, ownerCustomerId, applicantCustomerId, "NO_ACTIVE_CONTRACT"
                ));
                return;
            }

            if (tenants.existsByContractIdAndCustomerIdAndLeftAtIsNull(locked.getId(), applicantCustomerId) ||
                    tenants.existsCurrentTenantInContractWithStatus(applicantCustomerId, ContractStatus.ACTIVE)) {
                outbox.publishTenantAddRejected(new com.rental.contracttenant.outbox.TenantAddRejectedPayload(
                        roomId, buildingId, applicationId, ownerCustomerId, applicantCustomerId, "ALREADY_IN_ROOM"
                ));
                return;
            }

            Integer maxCapacity;
            try {
                PropertyClient.RoomInfo room = properties.getRoom(locked.getRoomId());
                maxCapacity = room == null ? null : room.maxCapacity();
            } catch (Exception unavailable) {
                throw new BusinessException("DEPENDENCY_UNAVAILABLE", "Khong kiem tra duoc suc chua phong", HttpStatus.SERVICE_UNAVAILABLE);
            }
            if (maxCapacity == null || maxCapacity < 1) {
                throw new BusinessException("DEPENDENCY_UNAVAILABLE", "Khong kiem tra duoc suc chua phong", HttpStatus.SERVICE_UNAVAILABLE);
            }

            long currentOccupancy = tenants.countByContractIdAndLeftAtIsNull(locked.getId());
            if (currentOccupancy + 1 > maxCapacity) {
                outbox.publishTenantAddRejected(new com.rental.contracttenant.outbox.TenantAddRejectedPayload(
                        roomId, buildingId, applicationId, ownerCustomerId, applicantCustomerId, "ROOM_FULL"
                ));
                return;
            }

            ContractTenant tenant = new ContractTenant();
            tenant.setContractId(locked.getId());
            tenant.setCustomerId(applicantCustomerId);
            tenant.setJoinedAt(LocalDate.now(clock));
            tenant.setLeftAt(null);
            tenant.setRepresentative(false);
            tenants.save(tenant);

            outbox.publishTenantAdded(new com.rental.contracttenant.outbox.TenantAddedPayload(
                    locked.getId(), locked.getRoomId(), locked.getBuildingId(),
                    applicantCustomerId, applicationId, ownerCustomerId
            ));
        });
    }

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

    private static ContractTenant initialRepresentative(Contract contract) {
        ContractTenant tenant = new ContractTenant();
        tenant.setContractId(contract.getId());
        tenant.setCustomerId(contract.getCustomerId());
        tenant.setJoinedAt(contract.getStartDate());
        tenant.setLeftAt(null);
        tenant.setRepresentative(true);
        return tenant;
    }

    private static String signKey(String kind, Long id) {
        return kind + ":" + id;
    }

    private static final String DUPLICATE_CUSTOMER_INDEX = "uq_contract_tenant_active_customer";
    private static final String REPRESENTATIVE_INDEX = "uq_contract_tenant_representative";
    private static final String PENDING_MOVE_OUT_INDEX = "uq_move_out_request_pending";

    private static boolean isDuplicateActiveCustomer(DataIntegrityViolationException violation) {
        return violates(violation, DUPLICATE_CUSTOMER_INDEX);
    }

    private static boolean violates(DataIntegrityViolationException violation, String index) {
        return index.equals(constraintName(violation))
                || String.valueOf(violation.getMostSpecificCause().getMessage()).contains(index);
    }

    private static String constraintName(DataIntegrityViolationException violation) {
        for (Throwable cause = violation; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException named) {
                return named.getConstraintName();
            }
        }
        return null;
    }

    private static BigDecimal normalizeMoney(BigDecimal amount) {
        return amount.stripTrailingZeros().scale() < 0 ? amount.setScale(0) : amount;
    }

    private static HeaderUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof HeaderUser user)) {
            throw new AccessDeniedException("Khong xac dinh duoc nguoi dung");
        }
        return user;
    }
}
