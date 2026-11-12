package com.rental.contracttenant.service;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.error.NotFoundException;
import com.rental.common.security.HeaderUser;
import com.rental.contracttenant.client.AuthClient;
import com.rental.contracttenant.client.CustomerClient;
import com.rental.contracttenant.client.PropertyClient;
import com.rental.contracttenant.config.ContractRenewalSettings;
import com.rental.contracttenant.config.ContractSignatureSettings;
import com.rental.contracttenant.dto.AddTenantRequest;
import com.rental.contracttenant.dto.BillingContractData;
import com.rental.contracttenant.dto.ChatContractContext;
import com.rental.contracttenant.dto.ContractDetailResponse;
import com.rental.contracttenant.dto.ContractDocumentResponse;
import com.rental.contracttenant.dto.ContractResponse;
import com.rental.contracttenant.dto.ContractSignatureResponse;
import com.rental.contracttenant.dto.ContractTenantResponse;
import com.rental.contracttenant.dto.CreateDraftContractRequest;
import com.rental.contracttenant.dto.CreateDraftManualRequest;
import com.rental.contracttenant.dto.CreateMoveOutRequest;
import com.rental.contracttenant.dto.DeclineSignatureRequest;
import com.rental.contracttenant.dto.InternalContractDepositResponse;
import com.rental.contracttenant.dto.MarkDepositPaidRequest;
import com.rental.contracttenant.dto.MoveOutRequestResponse;
import com.rental.contracttenant.dto.RejectMoveOutRequest;
import com.rental.contracttenant.dto.RejectRenewalRequest;
import com.rental.contracttenant.dto.RenewContractRequest;
import com.rental.contracttenant.dto.RenewalRequestResponse;
import com.rental.contracttenant.dto.RoomTenantCheckResponse;
import com.rental.contracttenant.dto.RoommateApplicationAcceptedEvent;
import com.rental.contracttenant.dto.SettleMoveOutRefundRequest;
import com.rental.contracttenant.dto.SignContractRequest;
import com.rental.contracttenant.dto.TenantLeaveRequest;
import com.rental.contracttenant.dto.TenantRenewalRequest;
import com.rental.contracttenant.dto.TerminateContractRequest;
import com.rental.contracttenant.dto.UpdateDraftContractRequest;
import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractDocument;
import com.rental.contracttenant.entity.ContractRenewalRequest;
import com.rental.contracttenant.entity.ContractSignature;
import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.entity.ContractTenant;
import com.rental.contracttenant.entity.MoveOutRequest;
import com.rental.contracttenant.entity.MoveOutStatus;
import com.rental.contracttenant.entity.RenewalRequestStatus;
import com.rental.contracttenant.entity.SignatureParty;
import com.rental.contracttenant.entity.SignatureStatus;
import com.rental.contracttenant.outbox.ContractEndedPayload;
import com.rental.contracttenant.outbox.ContractRenewalDecidedPayload;
import com.rental.contracttenant.outbox.ContractRenewalRequestedPayload;
import com.rental.contracttenant.outbox.ContractRenewedPayload;
import com.rental.contracttenant.outbox.ContractSignatureDeclinedPayload;
import com.rental.contracttenant.outbox.ContractSignatureExpiredPayload;
import com.rental.contracttenant.outbox.MoveOutApprovedPayload;
import com.rental.contracttenant.outbox.MoveOutRequestedPayload;
import com.rental.contracttenant.outbox.OutboxEventPublisher;
import com.rental.contracttenant.outbox.TenantAddRejectedPayload;
import com.rental.contracttenant.outbox.TenantAddedPayload;
import com.rental.contracttenant.outbox.TenantRemovedPayload;
import com.rental.contracttenant.repository.ContractDocumentRepository;
import com.rental.contracttenant.repository.ContractRenewalRequestRepository;
import com.rental.contracttenant.repository.ContractRepository;
import com.rental.contracttenant.repository.ContractSignatureRepository;
import com.rental.contracttenant.repository.ContractTenantRepository;
import com.rental.contracttenant.repository.MoveOutRequestRepository;
import feign.FeignException;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContractService {

    public record SignatureContext(String ipAddress, String userAgent) {}

    private final ContractRepository contracts;
    private final ContractTenantRepository tenants;
    private final MoveOutRequestRepository moveOutRequests;
    private final ContractRenewalRequestRepository renewalRequests;
    private final ContractSignatureRepository signatureRows;
    private final ContractDocumentRepository documents;
    private final ContractDocumentService documentService;
    private final ContractDocumentStorage documentStorage;
    private final AuthClient authClient;
    private final ContractSignatureSettings signatureSettings;
    private final ContractRenewalSettings renewalSettings;
    private final OutboxEventPublisher outbox;
    private final PropertyClient properties;
    private final CustomerClient customers;
    private final BuildingScope buildingScope;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;

    public ContractResponse createDraft(CreateDraftContractRequest request) {
        if (request == null) {
            throw new BusinessException("VALIDATION_ERROR", "Request không được rỗng", HttpStatus.BAD_REQUEST);
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);

        try {
            return template.execute(status -> {
                return contracts.findByRentalRequestId(request.getRentalRequestId())
                        .map(existing -> {
                            validateReplay(existing, request);
                            if (existing.getSignatureStatus() == SignatureStatus.DECLINED) {
                                return ContractResponse.from(reviveDeclined(existing.getId(), request));
                            }
                            return ContractResponse.from(existing);
                        })
                        .orElseGet(() -> {
                            Contract saved = hydrateAndSave(contracts.save(toDraft(request)));
                            try {
                                documentService.generate(saved, List.of());
                            } catch (Exception e) {
                                log.warn("Không thể sinh tài liệu ban đầu cho hợp đồng nháp {}: {}", saved.getId(), e.getMessage());
                            }
                            return ContractResponse.from(saved);
                        });
            });
        } catch (DataIntegrityViolationException dive) {
            TransactionTemplate readOnlyTemplate = new TransactionTemplate(transactionManager);
            readOnlyTemplate.setReadOnly(true);

            Contract existing = readOnlyTemplate.execute(status ->
                    contracts.findByRentalRequestId(request.getRentalRequestId()).orElse(null));

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
            throw new BusinessException("RENTAL_REQUEST_CONFLICT", "Rental request đã gắn hợp đồng khác dữ liệu",
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
        contract.setDepositAmount(normalizeMoney(request.getDepositAmount()));
        contract.setMinTermMonths(request.getMinTermMonths());
        int termMonths = request.getMinTermMonths() != null && request.getMinTermMonths() > 0
                ? request.getMinTermMonths() : 12;
        if (request.getMoveInDate() != null) {
            contract.setEndDate(request.getMoveInDate().plusMonths(termMonths));
        }
        contract.setSignatureStatus(SignatureStatus.NOT_READY);

        // Snapshot everything the event carries. Dropping these fields here was why the PDF printed
        // "—" for the parties, the building and the move-in date.
        contract.setRoomNumber(request.getRoomNumber());
        contract.setBuildingName(request.getBuildingName());
        contract.setRoomAddress(request.getRoomAddress());
        contract.setElectricityPrice(normalizeMoney(request.getElectricityPrice()));
        contract.setWaterPrice(normalizeMoney(request.getWaterPrice()));
        contract.setWaterUnit(request.getWaterUnit());
        contract.setPaymentMethod(request.getPaymentMethod());
        contract.setPaymentDueDay(request.getPaymentDueDay());
        contract.setAvailableFrom(request.getAvailableFrom() != null
                ? request.getAvailableFrom() : request.getMoveInDate());

        contract.setLessorName(request.getLessorName());
        contract.setLessorDateOfBirth(request.getLessorDateOfBirth());
        contract.setLessorIdNumber(request.getLessorIdNumber());
        contract.setLessorIdIssueDate(request.getLessorIdIssueDate());
        contract.setLessorIdIssuePlace(request.getLessorIdIssuePlace());
        contract.setLessorPhone(request.getLessorPhone());
        contract.setLessorAddress(request.getLessorAddress());

        contract.setTenantFullName(request.getTenantFullName());
        contract.setTenantDateOfBirth(request.getTenantDateOfBirth());
        contract.setTenantGender(request.getTenantGender());
        contract.setTenantPhone(request.getTenantPhone());
        contract.setTenantEmail(request.getTenantEmail());
        contract.setTenantPermanentAddress(request.getTenantPermanentAddress());
        contract.setTenantOccupation(request.getTenantOccupation());
        contract.setTenantCccdNumber(request.getTenantCccdNumber());
        contract.setTenantCccdIssueDate(request.getTenantCccdIssueDate());
        contract.setTenantCccdIssuePlace(request.getTenantCccdIssuePlace());
        contract.setTenantCccdFrontKey(request.getTenantCccdFrontKey());
        contract.setTenantCccdBackKey(request.getTenantCccdBackKey());
        return contract;
    }

    private Contract reviveDeclined(Long id, CreateDraftContractRequest request) {
        Contract locked = contracts.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + id));
        locked.setStatus(ContractStatus.DRAFT);
        locked.setSignatureStatus(SignatureStatus.NOT_READY);
        locked.setTerminationReason(null);
        locked.setStartDate(request.getMoveInDate());
        locked.setRentAmount(normalizeMoney(request.getPrice()));
        locked.setDepositAmount(normalizeMoney(request.getDepositAmount()));
        locked.setMinTermMonths(request.getMinTermMonths());
        int reviveTermMonths = request.getMinTermMonths() != null && request.getMinTermMonths() > 0
                ? request.getMinTermMonths() : 12;
        if (request.getMoveInDate() != null) {
            locked.setEndDate(request.getMoveInDate().plusMonths(reviveTermMonths));
        }
        locked.setRoomNumber(request.getRoomNumber());
        locked.setBuildingName(request.getBuildingName());
        locked.setRoomAddress(request.getRoomAddress());
        locked.setElectricityPrice(normalizeMoney(request.getElectricityPrice()));
        locked.setWaterPrice(normalizeMoney(request.getWaterPrice()));
        locked.setWaterUnit(request.getWaterUnit());
        locked.setPaymentMethod(request.getPaymentMethod());
        locked.setPaymentDueDay(request.getPaymentDueDay());
        locked.setAvailableFrom(request.getAvailableFrom());
        locked.setLessorName(request.getLessorName());
        locked.setLessorDateOfBirth(request.getLessorDateOfBirth());
        locked.setLessorIdNumber(request.getLessorIdNumber());
        locked.setLessorIdIssueDate(request.getLessorIdIssueDate());
        locked.setLessorIdIssuePlace(request.getLessorIdIssuePlace());
        locked.setLessorPhone(request.getLessorPhone());
        locked.setLessorAddress(request.getLessorAddress());
        locked.setTenantFullName(request.getTenantFullName());
        locked.setTenantDateOfBirth(request.getTenantDateOfBirth());
        locked.setTenantGender(request.getTenantGender());
        locked.setTenantPhone(request.getTenantPhone());
        locked.setTenantEmail(request.getTenantEmail());
        locked.setTenantPermanentAddress(request.getTenantPermanentAddress());
        locked.setTenantOccupation(request.getTenantOccupation());
        locked.setTenantCccdNumber(request.getTenantCccdNumber());
        locked.setTenantCccdIssueDate(request.getTenantCccdIssueDate());
        locked.setTenantCccdIssuePlace(request.getTenantCccdIssuePlace());
        Contract saved = hydrateAndSave(contracts.save(locked));
        signatureRows.deleteAll(signatureRows.findByContractIdOrderBySignedAtAsc(saved.getId()));
        try {
            documentService.generate(saved, List.of());
        } catch (Exception e) {
            log.warn("Không thể sinh tài liệu khi revive hợp đồng {}: {}", saved.getId(), e.getMessage());
        }
        return saved;
    }

    public ContractResponse createManualDraft(CreateDraftManualRequest request) {
        if (request == null) {
            throw new BusinessException("VALIDATION_ERROR", "Request không được rỗng", HttpStatus.BAD_REQUEST);
        }
        if (request.getRoomId() == null || request.getBuildingId() == null || request.getCustomerId() == null) {
            throw new BusinessException("VALIDATION_ERROR", "Thiếu roomId, buildingId hoặc customerId", HttpStatus.BAD_REQUEST);
        }
        if (request.getStartDate() == null || request.getRentAmount() == null) {
            throw new BusinessException("VALIDATION_ERROR", "Thiếu startDate hoặc rentAmount", HttpStatus.BAD_REQUEST);
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            buildingScope.assertAssigned(request.getBuildingId());

            try {
                customers.checkExists(request.getCustomerId());
            } catch (NotFoundException nf) {
                throw nf;
            } catch (Exception unavailable) {
                throw new BusinessException("DEPENDENCY_UNAVAILABLE",
                        "Không xác thực được khách hàng", HttpStatus.SERVICE_UNAVAILABLE);
            }

            PropertyClient.RoomInfo room;
            try {
                room = properties.getRoom(request.getRoomId());
            } catch (NotFoundException nf) {
                throw new NotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng " + request.getRoomId());
            } catch (Exception unavailable) {
                throw new BusinessException("DEPENDENCY_UNAVAILABLE",
                        "Không xác thực được phòng", HttpStatus.SERVICE_UNAVAILABLE);
            }
            if (room == null || room.buildingId() == null) {
                throw new BusinessException("DEPENDENCY_UNAVAILABLE",
                        "Không xác thực được phòng", HttpStatus.SERVICE_UNAVAILABLE);
            }
            if (!room.buildingId().equals(request.getBuildingId())) {
                throw new BusinessException("ROOM_BUILDING_MISMATCH",
                        "Phòng không thuộc tòa nhà đã chọn", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            Contract contract = new Contract();
            contract.setRoomId(request.getRoomId());
            contract.setBuildingId(request.getBuildingId());
            contract.setCustomerId(request.getCustomerId());
            contract.setStatus(ContractStatus.DRAFT);
            contract.setStartDate(request.getStartDate());
            contract.setEndDate(request.getEndDate());
            contract.setRentAmount(normalizeMoney(request.getRentAmount()));
            contract.setDepositAmount(request.getDepositAmount() != null ? normalizeMoney(request.getDepositAmount()) : null);
            contract.setSignatureStatus(SignatureStatus.NOT_READY);

            contract.setRoomNumber(request.getRoomNumber());
            contract.setBuildingName(request.getBuildingName());
            contract.setRoomAddress(request.getRoomAddress());
            contract.setElectricityPrice(normalizeMoney(request.getElectricityPrice()));
            contract.setWaterPrice(normalizeMoney(request.getWaterPrice()));
            contract.setWaterUnit(request.getWaterUnit());
            contract.setPaymentMethod(request.getPaymentMethod());
            contract.setPaymentDueDay(request.getPaymentDueDay());
            contract.setMinTermMonths(request.getMinTermMonths());
            contract.setAvailableFrom(request.getAvailableFrom() != null ? request.getAvailableFrom() : request.getStartDate());

            contract.setLessorName(request.getLessorName());
            contract.setLessorDateOfBirth(request.getLessorDateOfBirth());
            contract.setLessorIdNumber(request.getLessorIdNumber());
            contract.setLessorIdIssueDate(request.getLessorIdIssueDate());
            contract.setLessorIdIssuePlace(request.getLessorIdIssuePlace());
            contract.setLessorPhone(request.getLessorPhone());
            contract.setLessorAddress(request.getLessorAddress());

            contract.setTenantFullName(request.getTenantFullName());
            contract.setTenantDateOfBirth(request.getTenantDateOfBirth());
            contract.setTenantGender(request.getTenantGender());
            contract.setTenantPhone(request.getTenantPhone());
            contract.setTenantEmail(request.getTenantEmail());
            contract.setTenantPermanentAddress(request.getTenantPermanentAddress());
            contract.setTenantOccupation(request.getTenantOccupation());
            contract.setTenantCccdNumber(request.getTenantCccdNumber());
            contract.setTenantCccdIssueDate(request.getTenantCccdIssueDate());
            contract.setTenantCccdIssuePlace(request.getTenantCccdIssuePlace());

            Contract saved = hydrateAndSave(contracts.save(contract));
            try {
                documentService.generate(saved, List.of());
            } catch (Exception e) {
                log.warn("Không thể sinh PDF cho hợp đồng thủ công {}: {}", saved.getId(), e.getMessage());
            }
            return ContractResponse.from(saved);
        });
    }

    public ContractResponse createDraftManual(CreateDraftManualRequest request) {
        return createManualDraft(request);
    }

    public ContractResponse updateDraftDetails(Long id, UpdateDraftContractRequest request) {
        if (request == null) {
            throw new BusinessException("VALIDATION_ERROR", "Request không được rỗng", HttpStatus.BAD_REQUEST);
        }
        if (request.getStartDate() == null) {
            throw new BusinessException("VALIDATION_ERROR", "Ngày bắt đầu không được rỗng", HttpStatus.BAD_REQUEST);
        }
        if (request.getEndDate() != null && !request.getEndDate().isAfter(request.getStartDate())) {
            throw new BusinessException("INVALID_DATE", "Ngày kết thúc phải sau ngày bắt đầu", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            Contract contract = contracts.findById(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + id));
            buildingScope.assertAssigned(contract.getBuildingId());

            Contract locked = contracts.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + id));

            if (locked.getStatus() != ContractStatus.DRAFT) {
                throw new BusinessException("INVALID_STATUS", "Chỉ sửa hợp đồng ở trạng thái DRAFT", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (locked.getSignatureStatus() != SignatureStatus.NOT_READY) {
                throw new BusinessException("INVALID_SIGNATURE_STATUS",
                        "Không thể sửa hợp đồng sau khi đã gửi khách ký", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            applyDetails(locked, request);
            Contract saved = hydrateAndSave(contracts.save(locked));
            try {
                documentService.generate(saved, pdfTenants(saved));
            } catch (Exception e) {
                log.warn("Không thể cập nhật tài liệu hợp đồng {}: {}", saved.getId(), e.getMessage());
            }
            return ContractResponse.from(saved);
        });
    }

    private static void applyDetails(Contract contract, UpdateDraftContractRequest request) {
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setRentAmount(normalizeMoney(request.getRentAmount()));
        contract.setDepositAmount(request.getDepositAmount() == null ? null : normalizeMoney(request.getDepositAmount()));
        if (request.getElectricityPrice() != null) {
            contract.setElectricityPrice(request.getElectricityPrice());
        }
        if (request.getWaterPrice() != null) {
            contract.setWaterPrice(request.getWaterPrice());
        }
        if (request.getWaterUnit() != null) {
            contract.setWaterUnit(request.getWaterUnit());
        }
        if (request.getPaymentMethod() != null) {
            contract.setPaymentMethod(request.getPaymentMethod());
        }
        if (request.getPaymentDueDay() != null) {
            contract.setPaymentDueDay(request.getPaymentDueDay());
        }
        if (request.getMinTermMonths() != null) {
            contract.setMinTermMonths(request.getMinTermMonths());
        }
        if (request.getAvailableFrom() != null) {
            contract.setAvailableFrom(request.getAvailableFrom());
        }

        setIfPresent(request.getRoomNumber(), contract::setRoomNumber);
        setIfPresent(request.getBuildingName(), contract::setBuildingName);
        setIfPresent(request.getRoomAddress(), contract::setRoomAddress);
        setIfPresent(request.getLessorName(), contract::setLessorName);
        if (request.getLessorDateOfBirth() != null) {
            contract.setLessorDateOfBirth(request.getLessorDateOfBirth());
        }
        setIfPresent(request.getLessorIdNumber(), contract::setLessorIdNumber);
        if (request.getLessorIdIssueDate() != null) {
            contract.setLessorIdIssueDate(request.getLessorIdIssueDate());
        }
        setIfPresent(request.getLessorIdIssuePlace(), contract::setLessorIdIssuePlace);
        setIfPresent(request.getLessorPhone(), contract::setLessorPhone);
        setIfPresent(request.getLessorAddress(), contract::setLessorAddress);

        setIfPresent(request.getTenantFullName(), contract::setTenantFullName);
        if (request.getTenantDateOfBirth() != null) {
            contract.setTenantDateOfBirth(request.getTenantDateOfBirth());
        }
        setIfPresent(request.getTenantGender(), contract::setTenantGender);
        setIfPresent(request.getTenantPhone(), contract::setTenantPhone);
        setIfPresent(request.getTenantEmail(), contract::setTenantEmail);
        setIfPresent(request.getTenantPermanentAddress(), contract::setTenantPermanentAddress);
        setIfPresent(request.getTenantOccupation(), contract::setTenantOccupation);
        setIfPresent(request.getTenantCccdNumber(), contract::setTenantCccdNumber);
        if (request.getTenantCccdIssueDate() != null) {
            contract.setTenantCccdIssueDate(request.getTenantCccdIssueDate());
        }
        setIfPresent(request.getTenantCccdIssuePlace(), contract::setTenantCccdIssuePlace);
    }

    private static void setIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank()) {
            setter.accept(value.trim());
        }
    }

    public ContractDocumentStorage.Download openDocument(Long id, boolean refresh) {
        Contract contract = contracts.findById(id)
                .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + id));
        assertCanView(contract);

        ContractDocument doc = documents.findFirstByContractIdAndDocumentTypeOrderByVersionDesc(id, "CONTRACT").orElse(null);
        if (doc == null || refresh) {
            try {
                doc = documentService.generate(contract, pdfTenants(contract));
            } catch (Exception ex) {
                log.warn("Lỗi khi tự sinh tài liệu cho hợp đồng {}: {}", id, ex.getMessage());
            }
        }
        if (doc == null) {
            throw new NotFoundException("DOCUMENT_NOT_FOUND", "Hợp đồng chưa có tài liệu PDF");
        }
        return documentStorage.open(doc.getObjectKey());
    }

    public String uploadSignatureImage(Long id, MultipartFile file) {
        Contract contract = contracts.findById(id)
                .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + id));
        assertCanView(contract);
        HeaderUser user = currentUser();
        boolean isStaffOrAdmin = user.getRoles().contains("ADMIN") || user.getRoles().contains("QUAN_LY");
        SignatureParty party = isStaffOrAdmin ? SignatureParty.LESSOR : SignatureParty.TENANT;
        return documentStorage.uploadSignatureImage(id, party, file);
    }

    public ContractResponse signContract(Long id, SignContractRequest request, SignatureContext context) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            Contract contract = contracts.findById(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + id));
            HeaderUser user = currentUser();
            boolean isStaffOrAdmin = user.getRoles().contains("ADMIN") || user.getRoles().contains("QUAN_LY");

            contracts.lockNamedKey(signKey("room", contract.getRoomId()));
            Contract locked = contracts.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + id));

            if (locked.getSignatureStatus() == SignatureStatus.NOT_READY) {
                throw new BusinessException("NOT_READY", "Hợp đồng chưa mở ký", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (locked.getSignatureStatus() == SignatureStatus.COMPLETED) {
                throw new BusinessException("ALREADY_COMPLETED", "Hợp đồng đã hoàn tất ký kết", HttpStatus.CONFLICT);
            }

            Instant now = Instant.now(clock);
            if (locked.getSignatureDeadline() != null && now.isAfter(locked.getSignatureDeadline())) {
                expireLocked(locked);
                throw new BusinessException("SIGNATURE_EXPIRED", "Hạn ký hợp đồng đã kết thúc", HttpStatus.GONE);
            }

            SignatureParty party = isStaffOrAdmin ? SignatureParty.LESSOR : SignatureParty.TENANT;
            if (party == SignatureParty.TENANT && locked.getSignatureStatus() != SignatureStatus.AWAITING_TENANT) {
                throw new BusinessException("INVALID_ORDER", "Chưa đến lượt khách thuê ký", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (party == SignatureParty.LESSOR && locked.getSignatureStatus() != SignatureStatus.AWAITING_LESSOR) {
                throw new BusinessException("INVALID_ORDER", "Khách thuê chưa ký hoặc trạng thái không hợp lệ", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            if (request != null && request.password() != null && !request.password().isBlank()) {
                try {
                    authClient.verifyPassword(new AuthClient.VerifyPasswordRequest(Long.valueOf(user.getUserId()), request.password()));
                } catch (Exception ex) {
                    throw new BusinessException("INVALID_PASSWORD", "Mật khẩu xác thực ký không đúng", HttpStatus.UNAUTHORIZED);
                }
            }

            ContractSignature sig = new ContractSignature();
            sig.setContractId(locked.getId());
            sig.setParty(party);
            sig.setSignerId(Long.valueOf(user.getUserId()));
            sig.setSignerName(party == SignatureParty.TENANT ? locked.getTenantFullName() : locked.getLessorName());
            sig.setSignedAt(now);
            sig.setImageKey(request != null ? request.signatureImageKey() : null);
            sig.setIp(context != null ? context.ipAddress() : null);
            sig.setUserAgent(context != null ? context.userAgent() : null);
            sig.setConsentVersion(request != null ? request.consentVersion() : "v1.0");
            signatureRows.save(sig);

            if (party == SignatureParty.TENANT) {
                if (!locked.isDepositPaid() && request != null && Boolean.TRUE.equals(request.depositConfirmed())
                        && locked.getDepositAmount() != null && locked.getDepositAmount().signum() > 0) {
                    locked.setDepositPaid(true);
                    locked.setDepositPaidAt(now);
                    locked.setDepositPaymentMethod("MANUAL_TRANSFER");
                }
                locked.setSignatureStatus(SignatureStatus.AWAITING_LESSOR);
                outbox.publishContractTenantSigned(locked, now);
            } else {
                locked.setSignatureStatus(SignatureStatus.COMPLETED);
                locked.setStatus(ContractStatus.ACTIVE);
                ContractTenant representative = null;
                try {
                    representative = tenants.save(initialRepresentative(locked));
                } catch (Exception ignore) {}
                // Contract activation and the representative assignment are two projection events;
                // each must advance the contract revision accepted by the Property projection.
                locked.setRevision(locked.getRevision() + 1);
                outbox.publishContractSigned(locked, List.of(locked.getCustomerId()));
                // Chi nhac ghi chi so ban giao cho hop dong vao o hom nay/tuong lai; hop dong
                // kich hoat lai tu du lieu cu se lam ngap chuong ky thuat neu khong loc.
                if (locked.getStartDate() != null && !locked.getStartDate().isBefore(LocalDate.now(clock))) {
                    outbox.publishMeterReadingHandoverDue(locked);
                }
                if (representative != null) {
                    locked.setRevision(locked.getRevision() + 1);
                    outbox.publishTenantAdded(new TenantAddedPayload(
                            locked.getId(), locked.getRoomId(), locked.getBuildingId(),
                            representative.getId(), representative.getCustomerId(), representative.getJoinedAt(),
                            null, locked.getCustomerId(), locked.getRevision()));
                }
            }

            Contract saved = contracts.save(locked);
            try {
                documentService.generate(saved, pdfTenants(saved));
            } catch (Exception ex) {
                log.warn("Không thể sinh PDF sau khi ký hợp đồng {}: {}", saved.getId(), ex.getMessage());
            }
            return ContractResponse.from(saved);
        });
    }

    public ContractResponse declineSignature(Long id, DeclineSignatureRequest request) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            Contract contract = contracts.findById(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + id));
            HeaderUser user = currentUser();
            assertTenantOwnsContract(contract, Long.valueOf(user.getUserId()));

            Contract locked = contracts.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + id));

            if (locked.getSignatureStatus() != SignatureStatus.AWAITING_TENANT) {
                throw new BusinessException("INVALID_STATUS", "Chỉ có thể từ chối ký khi đang ở trạng thái chờ khách ký", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            locked.setSignatureStatus(SignatureStatus.DECLINED);
            locked.setStatus(ContractStatus.TERMINATED);
            locked.setTerminationReason(request != null ? request.reason() : "Khách thuê từ chối ký");
            Contract saved = contracts.save(locked);

            closeRenewalRequest(saved, "Khách thuê từ chối ký hợp đồng gia hạn");

            outbox.publishContractSignatureDeclined(new ContractSignatureDeclinedPayload(
                    saved.getId(), saved.getRentalRequestId(), saved.getRoomId(), saved.getBuildingId(),
                    saved.getCustomerId(), locked.getTerminationReason()));

            return ContractResponse.from(saved);
        });
    }

    public void expireSignatureContract(Long id) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            Contract locked = contracts.findByIdForUpdate(id).orElse(null);
            if (locked == null) return;
            if (locked.getSignatureStatus() != SignatureStatus.AWAITING_TENANT
                    && locked.getSignatureStatus() != SignatureStatus.AWAITING_LESSOR) {
                return;
            }
            if (locked.getSignatureDeadline() == null || Instant.now(clock).isBefore(locked.getSignatureDeadline())) {
                return;
            }
            expireLocked(locked);
        });
    }

    private void expireLocked(Contract locked) {
        locked.setSignatureStatus(SignatureStatus.EXPIRED);
        locked.setStatus(ContractStatus.TERMINATED);
        locked.setTerminationReason("Quá hạn ký hợp đồng điện tử");
        Contract saved = contracts.save(locked);
        closeRenewalRequest(saved, "Hợp đồng gia hạn quá hạn ký điện tử");
        outbox.publishContractSignatureExpired(new ContractSignatureExpiredPayload(
                saved.getId(), saved.getRentalRequestId(), saved.getRoomId(), saved.getBuildingId(),
                saved.getCustomerId()));
    }

    /** A declined/expired renewal must free the contract so the tenant can ask again. */
    private void closeRenewalRequest(Contract renewalContract, String reason) {
        if (renewalContract.getRenewalRequestId() == null) {
            return;
        }
        renewalRequests.findById(renewalContract.getRenewalRequestId()).ifPresent(request -> {
            if (request.getStatus() == RenewalRequestStatus.APPROVED) {
                request.setStatus(RenewalRequestStatus.REJECTED);
                request.setRejectReason(reason);
                request.setDecidedAt(Instant.now(clock));
                renewalRequests.save(request);
            }
        });
    }

    public ContractResponse offerRenewal(Long id, RenewContractRequest request) {
        LocalDate reqEndDate = request != null ? request.getNewEndDate() : null;

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            Contract contract = contracts.findById(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + id));
            buildingScope.assertAssigned(contract.getBuildingId());

            contracts.lockNamedKey(signKey("room", contract.getRoomId()));
            Contract locked = contracts.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + id));
            if (locked.getStatus() != ContractStatus.ACTIVE) {
                throw new BusinessException("INVALID_STATUS", "Chỉ gia hạn hợp đồng ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (locked.getEndDate() == null) {
                throw new BusinessException("INVALID_DATE", "Hợp đồng chưa có ngày kết thúc", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            LocalDate start = locked.getEndDate().plusDays(1);
            LocalDate newEndDate = reqEndDate != null ? reqEndDate : locked.getEndDate().plusYears(1);
            if (!newEndDate.isAfter(start)) {
                throw new BusinessException("INVALID_DATE",
                        "Ngày kết thúc gia hạn phải sau ngày bắt đầu " + start, HttpStatus.UNPROCESSABLE_ENTITY);
            }

            Contract renewal = contracts.findFirstByRenewedFromContractIdOrderByCreatedAtDesc(id)
                    .map(existing -> contracts.findByIdForUpdate(existing.getId()).orElse(existing))
                    .orElse(null);

            if (renewal != null) {
                if (renewal.getStatus() == ContractStatus.ACTIVE
                        || renewal.getSignatureStatus() == SignatureStatus.COMPLETED) {
                    throw new BusinessException("ALREADY_RENEWED",
                            "Hợp đồng này đã có hợp đồng gia hạn có hiệu lực", HttpStatus.CONFLICT);
                }
                if (renewal.getSignatureStatus() == SignatureStatus.AWAITING_LESSOR) {
                    throw new BusinessException("INVALID_SIGNATURE_STATUS",
                            "Khách đã ký hợp đồng gia hạn, không thể sửa hoặc gửi lại", HttpStatus.UNPROCESSABLE_ENTITY);
                }
                renewal.setStartDate(start);
                renewal.setEndDate(newEndDate);
            } else {
                if (contracts.existsNextActiveContract(locked.getRoomId(), ContractStatus.ACTIVE, locked.getEndDate())) {
                    throw new BusinessException("ROOM_ALREADY_CONTRACTED",
                            "Phòng đã có hợp đồng khác bắt đầu sau ngày kết thúc này", HttpStatus.CONFLICT);
                }
                renewal = renewalDraft(locked, start, newEndDate);
            }

            renewal.setSignatureStatus(SignatureStatus.AWAITING_TENANT);
            renewal.setSignatureDeadline(Instant.now(clock).plus(signatureSettings.deadline()));
            renewal.setTerminationReason(null);
            Contract saved = contracts.save(renewal);
            try {
                documentService.generate(saved, pdfTenants(saved));
            } catch (Exception ex) {
                log.warn("Không thể sinh PDF khi gửi đề nghị gia hạn {}: {}", saved.getId(), ex.getMessage());
            }
            return ContractResponse.from(saved);
        });
    }

    public ContractResponse renewContract(Long id, RenewContractRequest request) {
        return offerRenewal(id, request);
    }

    /**
     * Tenant asks for a renewal. This deliberately creates ONLY a request row: no contract, no PDF.
     * A manager must approve it, then explicitly prepare and send the renewal contract.
     */
    public RenewalRequestResponse requestRenewal(Long id, TenantRenewalRequest request) {
        int termMonths = (request != null && request.getTermMonths() != null && request.getTermMonths() > 0)
                ? request.getTermMonths() : renewalSettings.defaultTermMonths();
        if (termMonths > 60) {
            throw new BusinessException("INVALID_TERM", "Kỳ hạn gia hạn tối đa là 60 tháng", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            HeaderUser user = currentUser();
            Long customerId = Long.valueOf(user.getUserId());

            Contract contract = contracts.findById(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + id));
            assertTenantOwnsContract(contract, customerId);

            contracts.lockNamedKey(signKey("room", contract.getRoomId()));
            Contract locked = contracts.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + id));
            validateRenewableContract(locked, LocalDate.now(clock));
            assertNoRenewalInProgress(locked);

            if (renewalRequests.existsByContractIdAndStatus(locked.getId(), RenewalRequestStatus.PENDING)) {
                throw new BusinessException("REQUEST_ALREADY_EXISTS",
                        "Đã có yêu cầu gia hạn đang chờ duyệt", HttpStatus.CONFLICT);
            }

            ContractRenewalRequest renewal = new ContractRenewalRequest();
            renewal.setContractId(locked.getId());
            renewal.setRequestedBy(customerId);
            renewal.setTermMonths(termMonths);
            renewal.setNote(request != null ? request.getNote() : null);
            renewal.setStatus(RenewalRequestStatus.PENDING);

            ContractRenewalRequest saved;
            try {
                saved = renewalRequests.saveAndFlush(renewal);
            } catch (DataIntegrityViolationException concurrent) {
                throw new BusinessException("REQUEST_ALREADY_EXISTS",
                        "Đã có yêu cầu gia hạn đang chờ duyệt", HttpStatus.CONFLICT);
            }

            LocalDate start = locked.getEndDate().plusDays(1);
            LocalDate end = start.plusMonths(termMonths);
            outbox.publishContractRenewalRequested(ContractRenewalRequestedPayload.builder()
                    .contractId(locked.getId())
                    .oldContractId(locked.getId())
                    .roomId(locked.getRoomId())
                    .buildingId(locked.getBuildingId())
                    .customerId(customerId)
                    .roomNumber(locked.getRoomNumber())
                    .buildingName(locked.getBuildingName())
                    .startDate(start)
                    .endDate(end)
                    .termMonths(termMonths)
                    .build());

            return RenewalRequestResponse.from(saved, locked, null);
        });
    }

    public List<RenewalRequestResponse> findMyRenewalRequests() {
        Long customerId = Long.valueOf(currentUser().getUserId());
        return renewalRequests.findByRequestedByOrderByCreatedAtDesc(customerId).stream()
                .map(this::toRenewalResponse)
                .toList();
    }

    public Page<RenewalRequestResponse> searchRenewalRequests(Long buildingId, RenewalRequestStatus status,
            Pageable pageable) {
        if (buildingId != null) {
            buildingScope.assertAssigned(buildingId);
        }
        Collection<Long> allowed = buildingScope.getAssignedBuildingIds();
        return renewalRequests.findByFilters(buildingId, allowed, status, pageable)
                .map(this::toRenewalResponse);
    }

    public RenewalRequestResponse cancelRenewalRequest(Long requestId) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            Long customerId = Long.valueOf(currentUser().getUserId());
            ContractRenewalRequest request = renewalRequests.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new NotFoundException("RENEWAL_REQUEST_NOT_FOUND",
                            "Không tìm thấy yêu cầu gia hạn " + requestId));
            if (!Objects.equals(request.getRequestedBy(), customerId)) {
                throw new AccessDeniedException("Bạn không phải người gửi yêu cầu này");
            }
            if (request.getStatus() != RenewalRequestStatus.PENDING) {
                throw new BusinessException("INVALID_STATUS",
                        "Chỉ có thể hủy yêu cầu gia hạn đang chờ duyệt", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            request.setStatus(RenewalRequestStatus.CANCELLED);
            ContractRenewalRequest saved = renewalRequests.save(request);
            Contract old = contracts.findById(saved.getContractId()).orElseThrow(() ->
                    new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng"));
            return RenewalRequestResponse.from(saved, old, null);
        });
    }

    public RenewalRequestResponse approveRenewalRequest(Long requestId) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            HeaderUser user = currentUser();
            ContractRenewalRequest request = renewalRequests.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new NotFoundException("RENEWAL_REQUEST_NOT_FOUND",
                            "Không tìm thấy yêu cầu gia hạn " + requestId));
            Contract old = contracts.findById(request.getContractId()).orElseThrow(() ->
                    new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng"));
            buildingScope.assertAssigned(old.getBuildingId());

            if (request.getStatus() != RenewalRequestStatus.PENDING) {
                throw new BusinessException("INVALID_STATUS",
                        "Chỉ có thể duyệt yêu cầu đang chờ duyệt", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            validateRenewableContract(old, LocalDate.now(clock));

            request.setStatus(RenewalRequestStatus.APPROVED);
            request.setDecidedBy(Long.valueOf(user.getUserId()));
            request.setDecidedAt(Instant.now(clock));
            ContractRenewalRequest saved = renewalRequests.save(request);

            outbox.publishContractRenewalApproved(ContractRenewalDecidedPayload.builder()
                    .contractId(old.getId())
                    .renewalRequestId(saved.getId())
                    .roomId(old.getRoomId())
                    .buildingId(old.getBuildingId())
                    .customerId(old.getCustomerId())
                    .termMonths(saved.getTermMonths())
                    .roomNumber(old.getRoomNumber())
                    .buildingName(old.getBuildingName())
                    .build());

            Contract renewal = contracts.findByRenewalRequestId(saved.getId()).orElse(null);
            return RenewalRequestResponse.from(saved, old, renewal);
        });
    }

    public RenewalRequestResponse rejectRenewalRequest(Long requestId, RejectRenewalRequest request) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            HeaderUser user = currentUser();
            ContractRenewalRequest renewal = renewalRequests.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new NotFoundException("RENEWAL_REQUEST_NOT_FOUND",
                            "Không tìm thấy yêu cầu gia hạn " + requestId));
            Contract old = contracts.findById(renewal.getContractId()).orElseThrow(() ->
                    new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng"));
            buildingScope.assertAssigned(old.getBuildingId());

            if (renewal.getStatus() != RenewalRequestStatus.PENDING) {
                throw new BusinessException("INVALID_STATUS",
                        "Chỉ có thể từ chối yêu cầu đang chờ duyệt", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            String reason = request != null && request.getReason() != null && !request.getReason().isBlank()
                    ? request.getReason().trim() : "Ban quản lý từ chối yêu cầu gia hạn";
            renewal.setStatus(RenewalRequestStatus.REJECTED);
            renewal.setRejectReason(reason);
            renewal.setDecidedBy(Long.valueOf(user.getUserId()));
            renewal.setDecidedAt(Instant.now(clock));
            ContractRenewalRequest saved = renewalRequests.save(renewal);

            outbox.publishContractRenewalRejected(ContractRenewalDecidedPayload.builder()
                    .contractId(old.getId())
                    .renewalRequestId(saved.getId())
                    .roomId(old.getRoomId())
                    .buildingId(old.getBuildingId())
                    .customerId(old.getCustomerId())
                    .termMonths(saved.getTermMonths())
                    .roomNumber(old.getRoomNumber())
                    .buildingName(old.getBuildingName())
                    .reason(reason)
                    .build());

            return RenewalRequestResponse.from(saved, old, null);
        });
    }

    /** "Soạn hợp đồng": creates the full contract draft only after the request was approved. */
    public ContractResponse createRenewalDraftFromRequest(Long requestId) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            ContractRenewalRequest request = renewalRequests.findByIdForUpdate(requestId)
                    .orElseThrow(() -> new NotFoundException("RENEWAL_REQUEST_NOT_FOUND",
                            "Không tìm thấy yêu cầu gia hạn " + requestId));
            Contract old = contracts.findById(request.getContractId()).orElseThrow(() ->
                    new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng"));
            buildingScope.assertAssigned(old.getBuildingId());

            if (request.getStatus() != RenewalRequestStatus.APPROVED) {
                throw new BusinessException("INVALID_STATUS",
                        "Yêu cầu gia hạn phải được duyệt trước khi soạn hợp đồng",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            Contract existing = contracts.findByRenewalRequestId(request.getId()).orElse(null);
            if (existing != null) {
                return ContractResponse.from(existing);
            }

            contracts.lockNamedKey(signKey("room", old.getRoomId()));
            Contract locked = contracts.findByIdForUpdate(old.getId()).orElseThrow(() ->
                    new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng"));
            if (locked.getStatus() != ContractStatus.ACTIVE || locked.getEndDate() == null) {
                throw new BusinessException("INVALID_STATUS",
                        "Hợp đồng gốc không còn hiệu lực để gia hạn", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (contracts.existsNextActiveContract(locked.getRoomId(), ContractStatus.ACTIVE, locked.getEndDate())) {
                throw new BusinessException("ROOM_ALREADY_CONTRACTED",
                        "Phòng đã có hợp đồng khác bắt đầu sau ngày kết thúc này", HttpStatus.CONFLICT);
            }

            LocalDate start = locked.getEndDate().plusDays(1);
            LocalDate end = start.plusMonths(request.getTermMonths());
            Contract draft = renewalDraft(locked, start, end);
            draft.setRenewalRequestId(request.getId());
            Contract saved = hydrateAndSave(contracts.save(draft));
            try {
                documentService.generate(saved, pdfTenants(saved));
            } catch (Exception ex) {
                log.warn("Không thể sinh PDF bản nháp gia hạn {}: {}", saved.getId(), ex.getMessage());
            }
            return ContractResponse.from(saved);
        });
    }

    /** "Gửi khách ký" for a renewal draft; keeps the renewal-request origin requirement. */
    public ContractResponse sendRenewalDraft(Long contractId) {
        return openDraftSignature(contractId, true);
    }

    /** "Gửi khách ký" for any DRAFT (manual or rental-request contract) that is still NOT_READY. */
    public ContractResponse sendDraftForSignature(Long contractId) {
        return openDraftSignature(contractId, false);
    }

    private ContractResponse openDraftSignature(Long contractId, boolean renewalOnly) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            Contract contract = contracts.findById(contractId).orElseThrow(() ->
                    new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + contractId));
            buildingScope.assertAssigned(contract.getBuildingId());

            contracts.lockNamedKey(signKey("room", contract.getRoomId()));
            Contract locked = contracts.findByIdForUpdate(contractId).orElseThrow(() ->
                    new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + contractId));

            if (renewalOnly && locked.getRenewalRequestId() == null) {
                throw new BusinessException("NOT_RENEWAL_DRAFT",
                        "Chỉ gửi ký được cho hợp đồng gia hạn được tạo từ yêu cầu",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (locked.getStatus() != ContractStatus.DRAFT || locked.getSignatureStatus() != SignatureStatus.NOT_READY) {
                throw new BusinessException("INVALID_STATUS",
                        "Hợp đồng không ở trạng thái nháp chờ gửi ký", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (locked.getEndDate() == null || !locked.getEndDate().isAfter(locked.getStartDate())) {
                throw new BusinessException("INVALID_DATE",
                        "Cần nhập ngày kết thúc hợp đồng trước khi gửi khách ký",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }

            Contract hydrated = hydrateAndSave(locked);
            assertContractCompleteForSignature(hydrated);

            hydrated.setSignatureStatus(SignatureStatus.AWAITING_TENANT);
            hydrated.setSignatureDeadline(Instant.now(clock).plus(signatureSettings.deadline()));
            Contract saved = contracts.save(hydrated);
            documentService.generate(saved, pdfTenants(saved));
            return ContractResponse.from(saved);
        });
    }

    private RenewalRequestResponse toRenewalResponse(ContractRenewalRequest request) {
        Contract old = contracts.findById(request.getContractId()).orElseThrow(() ->
                new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng"));
        Contract renewal = contracts.findByRenewalRequestId(request.getId()).orElse(null);
        return RenewalRequestResponse.from(request, old, renewal);
    }

    private void assertNoRenewalInProgress(Contract contract) {
        Contract renewal = contracts.findFirstByRenewedFromContractIdOrderByCreatedAtDesc(contract.getId())
                .orElse(null);
        if (renewal != null) {
            boolean abandoned = renewal.getSignatureStatus() == SignatureStatus.DECLINED
                    || renewal.getSignatureStatus() == SignatureStatus.EXPIRED;
            if (!abandoned) {
                if (renewal.getStatus() == ContractStatus.ACTIVE
                        || renewal.getSignatureStatus() == SignatureStatus.COMPLETED) {
                    throw new BusinessException("ALREADY_RENEWED",
                            "Hợp đồng này đã có hợp đồng gia hạn có hiệu lực", HttpStatus.CONFLICT);
                }
                throw new BusinessException("RENEWAL_IN_PROGRESS",
                        "Đã có bản nháp hợp đồng gia hạn. Vui lòng liên hệ ban quản lý.", HttpStatus.CONFLICT);
            }
        }
        if (renewalRequests.findFirstByContractIdAndStatusOrderByCreatedAtDesc(
                contract.getId(), RenewalRequestStatus.APPROVED).isPresent()) {
            throw new BusinessException("RENEWAL_ALREADY_APPROVED",
                    "Yêu cầu gia hạn đã được duyệt, vui lòng chờ ban quản lý gửi hợp đồng.",
                    HttpStatus.CONFLICT);
        }
    }

    private void assertContractCompleteForSignature(Contract contract) {
        List<String> missing = new java.util.ArrayList<>();
        if (isBlank(contract.getRoomNumber())) missing.add("số phòng");
        if (isBlank(contract.getBuildingName())) missing.add("tên tòa nhà");
        if (isBlank(contract.getRoomAddress())) missing.add("địa chỉ phòng");
        if (contract.getRentAmount() == null) missing.add("giá thuê");
        if (contract.getElectricityPrice() == null) missing.add("tiền điện");
        if (contract.getWaterPrice() == null) missing.add("tiền nước");
        if (contract.getPaymentDueDay() == null) missing.add("hạn thanh toán");
        if (isBlank(contract.getLessorName())) missing.add("họ tên bên cho thuê");
        if (isBlank(contract.getLessorIdNumber())) missing.add("CCCD bên cho thuê");
        if (isBlank(contract.getLessorPhone())) missing.add("SĐT bên cho thuê");
        if (isBlank(contract.getLessorAddress())) missing.add("địa chỉ bên cho thuê");
        if (isBlank(contract.getTenantFullName())) missing.add("họ tên bên thuê");
        if (isBlank(contract.getTenantCccdNumber())) missing.add("CCCD bên thuê");
        if (isBlank(contract.getTenantPhone())) missing.add("SĐT bên thuê");
        if (!missing.isEmpty()) {
            throw new BusinessException("CONTRACT_INCOMPLETE",
                    "Hợp đồng còn thiếu: " + String.join(", ", missing)
                            + ". Vui lòng bổ sung đầy đủ trước khi gửi khách ký.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private Contract hydrateAndSave(Contract contract) {
        return hydrateSnapshot(contract) ? contracts.save(contract) : contract;
    }

    /**
     * Best-effort fill of snapshot columns for contracts created outside the rental-request event
     * flow (manual drafts, legacy rows). Never blocks the workflow: a dependency outage only leaves
     * the field blank, and "Gửi khách ký" then refuses to send an incomplete legal document.
     */
    private boolean hydrateSnapshot(Contract contract) {
        boolean changed = false;
        if (contract.getRoomId() != null) {
            try {
                PropertyClient.RoomInfo room = properties.getRoom(contract.getRoomId());
                if (room != null) {
                    if (isBlank(contract.getRoomNumber()) && !isBlank(room.roomNumber())) {
                        contract.setRoomNumber(room.roomNumber()); changed = true;
                    }
                    if (isBlank(contract.getBuildingName()) && !isBlank(room.buildingName())) {
                        contract.setBuildingName(room.buildingName()); changed = true;
                    }
                    if (isBlank(contract.getRoomAddress()) && !isBlank(room.buildingAddress())) {
                        contract.setRoomAddress(room.buildingAddress()); changed = true;
                    }
                    changed |= fillLessor(contract, room);
                }
            } catch (Exception ex) {
                log.warn("Không hydrate được snapshot phòng {}: {}", contract.getRoomId(), ex.getMessage());
            }
        }
        if (contract.getCustomerId() != null && (isBlank(contract.getTenantFullName())
                || isBlank(contract.getTenantPhone()) || isBlank(contract.getTenantEmail()))) {
            try {
                CustomerClient.CustomerProfile profile = customers.getCustomer(contract.getCustomerId());
                if (profile != null) {
                    if (isBlank(contract.getTenantFullName()) && !isBlank(profile.fullName())) {
                        contract.setTenantFullName(profile.fullName()); changed = true;
                    }
                    if (isBlank(contract.getTenantPhone()) && !isBlank(profile.phone())) {
                        contract.setTenantPhone(profile.phone()); changed = true;
                    }
                    if (isBlank(contract.getTenantEmail()) && !isBlank(profile.email())) {
                        contract.setTenantEmail(profile.email()); changed = true;
                    }
                }
            } catch (Exception ex) {
                log.warn("Không hydrate được thông tin khách {}: {}", contract.getCustomerId(), ex.getMessage());
            }
        }
        if (contract.getId() != null && (isBlank(contract.getTenantFullName())
                || isBlank(contract.getTenantCccdNumber()))) {
            ContractTenant representative = tenants.findByContractIdOrderByJoinedAtAsc(contract.getId()).stream()
                    .filter(t -> t.isRepresentative() && t.getLeftAt() == null)
                    .findFirst().orElse(null);
            if (representative != null) {
                if (isBlank(contract.getTenantFullName()) && !isBlank(representative.getFullName())) {
                    contract.setTenantFullName(representative.getFullName()); changed = true;
                }
                if (isBlank(contract.getTenantCccdNumber()) && !isBlank(representative.getCccdNumber())) {
                    contract.setTenantCccdNumber(representative.getCccdNumber()); changed = true;
                }
                if (isBlank(contract.getTenantPhone()) && !isBlank(representative.getPhone())) {
                    contract.setTenantPhone(representative.getPhone()); changed = true;
                }
                if (contract.getTenantDateOfBirth() == null && representative.getDateOfBirth() != null) {
                    contract.setTenantDateOfBirth(representative.getDateOfBirth()); changed = true;
                }
                if (isBlank(contract.getTenantGender()) && !isBlank(representative.getGender())) {
                    contract.setTenantGender(representative.getGender()); changed = true;
                }
                if (isBlank(contract.getTenantPermanentAddress()) && !isBlank(representative.getPermanentAddress())) {
                    contract.setTenantPermanentAddress(representative.getPermanentAddress()); changed = true;
                }
                if (isBlank(contract.getTenantOccupation()) && !isBlank(representative.getOccupation())) {
                    contract.setTenantOccupation(representative.getOccupation()); changed = true;
                }
                if (contract.getTenantCccdIssueDate() == null && representative.getCccdIssueDate() != null) {
                    contract.setTenantCccdIssueDate(representative.getCccdIssueDate()); changed = true;
                }
                if (isBlank(contract.getTenantCccdIssuePlace()) && !isBlank(representative.getCccdIssuePlace())) {
                    contract.setTenantCccdIssuePlace(representative.getCccdIssuePlace()); changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean fillLessor(Contract contract, PropertyClient.RoomInfo room) {
        boolean changed = false;
        if (isBlank(contract.getLessorName()) && !isBlank(room.lessorName())) {
            contract.setLessorName(room.lessorName()); changed = true;
        }
        if (contract.getLessorDateOfBirth() == null && room.lessorDateOfBirth() != null) {
            contract.setLessorDateOfBirth(room.lessorDateOfBirth()); changed = true;
        }
        if (isBlank(contract.getLessorIdNumber()) && !isBlank(room.lessorIdNumber())) {
            contract.setLessorIdNumber(room.lessorIdNumber()); changed = true;
        }
        if (contract.getLessorIdIssueDate() == null && room.lessorIdIssueDate() != null) {
            contract.setLessorIdIssueDate(room.lessorIdIssueDate()); changed = true;
        }
        if (isBlank(contract.getLessorIdIssuePlace()) && !isBlank(room.lessorIdIssuePlace())) {
            contract.setLessorIdIssuePlace(room.lessorIdIssuePlace()); changed = true;
        }
        if (isBlank(contract.getLessorPhone()) && !isBlank(room.lessorPhone())) {
            contract.setLessorPhone(room.lessorPhone()); changed = true;
        }
        if (isBlank(contract.getLessorAddress()) && !isBlank(room.lessorAddress())) {
            contract.setLessorAddress(room.lessorAddress()); changed = true;
        }
        return changed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public ContractResponse createRenewalDraft(Long oldContractId) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            Contract contract = contracts.findById(oldContractId).orElse(null);
            if (contract == null) return null;
            LocalDate today = LocalDate.now(clock);
            if (!renewalDraftIsDue(contract, today)) return null;

            contracts.lockNamedKey(signKey("room", contract.getRoomId()));
            Contract locked = contracts.findByIdForUpdate(oldContractId).orElse(null);
            if (locked == null || !renewalDraftIsDue(locked, today)
                    || contracts.existsByRenewedFromContractId(locked.getId())
                    || contracts.existsNextActiveContract(locked.getRoomId(), ContractStatus.ACTIVE, locked.getEndDate())) {
                return null;
            }

            LocalDate start = locked.getEndDate().plusDays(1);
            LocalDate end = start.plusMonths(renewalSettings.defaultTermMonths());
            Contract draft = renewalDraft(locked, start, end);
            Contract saved = contracts.save(draft);
            try {
                documentService.generate(saved, pdfTenants(saved));
            } catch (Exception e) {
                log.warn("Không thể sinh PDF cho bản nháp gia hạn {}: {}", saved.getId(), e.getMessage());
            }
            return ContractResponse.from(saved);
        });
    }

    public MoveOutRequestResponse createAutoMoveOut(Long contractId, LocalDate today) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            Contract contract = contracts.findById(contractId).orElse(null);
            if (contract == null) return null;
            contracts.lockNamedKey(signKey("room", contract.getRoomId()));
            Contract locked = contracts.findByIdForUpdate(contractId).orElse(null);
            if (locked == null || !autoMoveOutIsDue(locked, today)) return null;

            Contract renewal = contracts.findFirstByRenewedFromContractIdOrderByCreatedAtDesc(contractId).orElse(null);
            if (renewal != null && (renewal.getStatus() == ContractStatus.ACTIVE
                    || renewal.getSignatureStatus() == SignatureStatus.COMPLETED)) {
                return null;
            }
            if (moveOutRequests.existsByContractIdAndStatusIn(contractId,
                    List.of(MoveOutStatus.PENDING, MoveOutStatus.APPROVED, MoveOutStatus.COMPLETED))) {
                return null;
            }

            MoveOutRequest mor = new MoveOutRequest();
            mor.setContractId(locked.getId());
            mor.setRequestedBy(locked.getCustomerId());
            mor.setRequestDate(today);
            mor.setExpectedMoveOutDate(locked.getEndDate());
            mor.setReason("Hợp đồng hết hạn vào " + locked.getEndDate() + " và chưa được gia hạn.");
            mor.setStatus(MoveOutStatus.APPROVED);
            mor.setAutoGenerated(true);
            MoveOutRequest saved = moveOutRequests.save(mor);

            List<Long> tenantIds = tenants.findByContractIdAndLeftAtIsNullOrderByJoinedAtAsc(locked.getId()).stream()
                    .map(ContractTenant::getCustomerId)
                    .toList();
            if (tenantIds.isEmpty()) {
                tenantIds = List.of(locked.getCustomerId());
            }
            locked.setRevision(locked.getRevision() + 1);
            Contract savedContract = contracts.save(locked);

            outbox.publishMoveOutApproved(new MoveOutApprovedPayload(
                    saved.getId(), savedContract.getId(), savedContract.getRoomId(), savedContract.getBuildingId(),
                    saved.getExpectedMoveOutDate(), tenantIds, savedContract.getRevision()));

            return MoveOutRequestResponse.from(saved);
        });
    }

    private Contract renewalDraft(Contract locked, LocalDate start, LocalDate newEndDate) {
        Contract c = new Contract();
        c.setRenewedFromContractId(locked.getId());
        c.setRoomId(locked.getRoomId());
        c.setBuildingId(locked.getBuildingId());
        c.setCustomerId(locked.getCustomerId());
        c.setStatus(ContractStatus.DRAFT);
        c.setStartDate(start);
        c.setEndDate(newEndDate);
        c.setRentAmount(locked.getRentAmount());
        c.setDepositAmount(locked.getDepositAmount());
        c.setElectricityPrice(locked.getElectricityPrice());
        c.setWaterPrice(locked.getWaterPrice());
        c.setWaterUnit(locked.getWaterUnit());
        c.setPaymentMethod(locked.getPaymentMethod());
        c.setPaymentDueDay(locked.getPaymentDueDay());
        c.setMinTermMonths(locked.getMinTermMonths());
        c.setSignatureStatus(SignatureStatus.NOT_READY);

        // The renewal PDF must look like a real contract even when it was produced from an older
        // contract whose own snapshot was incomplete: copy everything, then fill the gaps below.
        c.setRoomNumber(locked.getRoomNumber());
        c.setBuildingName(locked.getBuildingName());
        c.setRoomAddress(locked.getRoomAddress());
        c.setAvailableFrom(start);
        c.setLessorName(locked.getLessorName());
        c.setLessorDateOfBirth(locked.getLessorDateOfBirth());
        c.setLessorIdNumber(locked.getLessorIdNumber());
        c.setLessorIdIssueDate(locked.getLessorIdIssueDate());
        c.setLessorIdIssuePlace(locked.getLessorIdIssuePlace());
        c.setLessorPhone(locked.getLessorPhone());
        c.setLessorAddress(locked.getLessorAddress());

        ContractTenant representative = tenants.findByContractIdOrderByJoinedAtAsc(locked.getId()).stream()
                .filter(t -> t.isRepresentative() && t.getLeftAt() == null)
                .findFirst().orElse(null);
        c.setTenantFullName(firstNonBlank(locked.getTenantFullName(),
                representative == null ? null : representative.getFullName()));
        c.setTenantDateOfBirth(locked.getTenantDateOfBirth() != null ? locked.getTenantDateOfBirth()
                : representative == null ? null : representative.getDateOfBirth());
        c.setTenantGender(firstNonBlank(locked.getTenantGender(),
                representative == null ? null : representative.getGender()));
        c.setTenantPhone(firstNonBlank(locked.getTenantPhone(),
                representative == null ? null : representative.getPhone()));
        c.setTenantEmail(firstNonBlank(locked.getTenantEmail(),
                representative == null ? null : representative.getEmail()));
        c.setTenantPermanentAddress(firstNonBlank(locked.getTenantPermanentAddress(),
                representative == null ? null : representative.getPermanentAddress()));
        c.setTenantOccupation(firstNonBlank(locked.getTenantOccupation(),
                representative == null ? null : representative.getOccupation()));
        c.setTenantCccdNumber(firstNonBlank(locked.getTenantCccdNumber(),
                representative == null ? null : representative.getCccdNumber()));
        c.setTenantCccdIssueDate(locked.getTenantCccdIssueDate() != null ? locked.getTenantCccdIssueDate()
                : representative == null ? null : representative.getCccdIssueDate());
        c.setTenantCccdIssuePlace(firstNonBlank(locked.getTenantCccdIssuePlace(),
                representative == null ? null : representative.getCccdIssuePlace()));
        c.setTenantCccdFrontKey(firstNonBlank(locked.getTenantCccdFrontKey(),
                representative == null ? null : representative.getCccdFrontKey()));
        c.setTenantCccdBackKey(firstNonBlank(locked.getTenantCccdBackKey(),
                representative == null ? null : representative.getCccdBackKey()));
        return c;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private void validateRenewableContract(Contract contract, LocalDate today) {
        if (contract.getStatus() != ContractStatus.ACTIVE) {
            throw new BusinessException("INVALID_STATUS", "Chỉ gia hạn hợp đồng đang ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (contract.getEndDate() == null) {
            throw new BusinessException("INVALID_STATUS", "Hợp đồng không có ngày kết thúc", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (contract.getTerminationReason() != null) {
            throw new BusinessException("CONTRACT_TERMINATING", "Hợp đồng đang trong quá trình chấm dứt", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        LocalDate minAllowed = contract.getEndDate().minusDays(30);
        if (today.isBefore(minAllowed)) {
            long daysLeft = ChronoUnit.DAYS.between(today, minAllowed);
            throw new BusinessException("TOO_EARLY_FOR_RENEWAL",
                    "Chưa đến thời hạn gia hạn hợp đồng. Quý khách chỉ có thể gửi yêu cầu gia hạn trước khi hợp đồng hết hạn 30 ngày (còn " + daysLeft + " ngày nữa).",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private boolean renewalDraftIsDue(Contract c, LocalDate today) {
        if (c.getStatus() != ContractStatus.ACTIVE || c.getEndDate() == null || c.getTerminationReason() != null) {
            return false;
        }
        LocalDate windowStart = c.getEndDate().minusDays(renewalSettings.leadDays());
        return !today.isBefore(windowStart) && !today.isAfter(c.getEndDate());
    }

    private boolean autoMoveOutIsDue(Contract c, LocalDate today) {
        if (c.getStatus() != ContractStatus.ACTIVE || c.getEndDate() == null || c.getTerminationReason() != null) {
            return false;
        }
        LocalDate windowStart = c.getEndDate().minusDays(renewalSettings.moveOutDays());
        return !today.isBefore(windowStart) && !today.isAfter(c.getEndDate());
    }

    public ContractTenantResponse addTenant(Long contractId, AddTenantRequest request) {
        if (request == null) {
            throw new BusinessException("VALIDATION_ERROR", "Request không được rỗng", HttpStatus.BAD_REQUEST);
        }
        if (request.getCustomerId() == null || request.getJoinedAt() == null) {
            throw new BusinessException("VALIDATION_ERROR", "Thiếu customerId hoặc joinedAt", HttpStatus.BAD_REQUEST);
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            Contract contract = contracts.findById(contractId)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + contractId));

            buildingScope.assertAssigned(contract.getBuildingId());

            if (contract.getStatus() != ContractStatus.ACTIVE) {
                throw new BusinessException("INVALID_STATUS", "Chỉ thêm người ở vào hợp đồng ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (request.getJoinedAt().isBefore(contract.getStartDate())) {
                throw new BusinessException("INVALID_DATE", "Ngày vào ở trước ngày bắt đầu hợp đồng", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            contracts.lockNamedKey(signKey("customer", request.getCustomerId()));
            contracts.lockNamedKey(signKey("room", contract.getRoomId()));

            Contract locked = contracts.findByIdForUpdate(contractId)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + contractId));
            if (!Objects.equals(locked.getRoomId(), contract.getRoomId())
                    || !Objects.equals(locked.getCustomerId(), contract.getCustomerId())) {
                throw new IllegalStateException("Hợp đồng " + contractId
                        + " đổi phòng hoặc khách giữa hai lần đọc; không thể đảm bảo thứ tự khóa");
            }
            if (locked.getStatus() != ContractStatus.ACTIVE) {
                throw new BusinessException("INVALID_STATUS", "Chỉ thêm người ở vào hợp đồng ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            if (tenants.existsByContractIdAndCustomerIdAndLeftAtIsNull(contractId, request.getCustomerId())) {
                throw new BusinessException("CUSTOMER_ALREADY_IN_ROOM", "Khách đã ở trong hợp đồng này", HttpStatus.CONFLICT);
            }
            if (tenants.existsCurrentTenantInContractWithStatus(request.getCustomerId(), ContractStatus.ACTIVE)) {
                throw new BusinessException("CUSTOMER_ALREADY_IN_ROOM", "Khách đã thuộc một hợp đồng ACTIVE khác", HttpStatus.CONFLICT);
            }

            Integer maxCapacity;
            try {
                PropertyClient.RoomInfo room = properties.getRoom(locked.getRoomId());
                maxCapacity = room == null ? null : room.maxCapacity();
            } catch (BusinessException | CallNotPermittedException | RetryableException unavailable) {
                throw new BusinessException("DEPENDENCY_UNAVAILABLE",
                        "Không kiểm tra được sức chứa phòng", HttpStatus.SERVICE_UNAVAILABLE);
            }
            if (maxCapacity == null || maxCapacity < 1) {
                throw new BusinessException("DEPENDENCY_UNAVAILABLE",
                        "Không kiểm tra được sức chứa phòng", HttpStatus.SERVICE_UNAVAILABLE);
            }

            long occupancy = tenants.countByContractIdAndLeftAtIsNull(contractId);
            if (occupancy + 1 > maxCapacity) {
                throw new BusinessException("ROOM_CAPACITY_EXCEEDED",
                        "Phòng đã đủ số người tối đa", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            ContractTenant tenant = new ContractTenant();
            tenant.setContractId(locked.getId());
            tenant.setCustomerId(request.getCustomerId());
            tenant.setJoinedAt(request.getJoinedAt());
            tenant.setLeftAt(null);
            tenant.setRepresentative(false);

            ContractTenant saved;
            try {
                saved = tenants.save(tenant);
            } catch (DataIntegrityViolationException race) {
                if (isDuplicateActiveCustomer(race)) {
                    throw new BusinessException("CUSTOMER_ALREADY_IN_ROOM",
                            "Khách đã ở trong hợp đồng này", HttpStatus.CONFLICT);
                }
                throw race;
            }

            outbox.publishTenantAdded(new TenantAddedPayload(
                    locked.getId(), locked.getRoomId(), locked.getBuildingId(), saved.getId(),
                    saved.getCustomerId(), saved.getJoinedAt(), null, locked.getCustomerId(),
                    nextRevision(locked)));

            return ContractTenantResponse.from(saved);
        });
    }

    public void changeRepresentative(Long contractId, Long tenantId) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            Contract contract = contracts.findById(contractId)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + contractId));
            buildingScope.assertAssigned(contract.getBuildingId());

            Contract locked = contracts.findByIdForUpdate(contractId)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + contractId));

            ContractTenant tenant = tenants.findByIdAndContractId(tenantId, contractId)
                    .orElseThrow(() -> new NotFoundException("TENANT_NOT_FOUND", "Không tìm thấy người ở " + tenantId));

            if (tenant.getLeftAt() != null) {
                throw new BusinessException("TENANT_NOT_ACTIVE", "Người ở đã rời khỏi hợp đồng", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            tenants.clearRepresentative(locked.getId());
            ContractTenant target = tenants.findById(tenantId)
                    .orElseThrow(() -> new NotFoundException("TENANT_NOT_FOUND", "Không tìm thấy người ở " + tenantId));
            target.setRepresentative(true);
            tenants.save(target);
        });
    }

    public ContractTenantResponse leaveTenant(Long contractId, Long tenantId, TenantLeaveRequest request) {
        if (request == null || request.getLeftAt() == null) {
            throw new BusinessException("VALIDATION_ERROR", "Thiếu leftAt", HttpStatus.BAD_REQUEST);
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            Contract contract = contracts.findById(contractId)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + contractId));
            buildingScope.assertAssigned(contract.getBuildingId());

            Contract locked = contracts.findByIdForUpdate(contractId)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + contractId));

            ContractTenant tenant = tenants.findByIdAndContractId(tenantId, contractId)
                    .orElseThrow(() -> new NotFoundException("TENANT_NOT_FOUND", "Không tìm thấy người ở " + tenantId));

            if (tenant.getLeftAt() != null) {
                throw new BusinessException("TENANT_NOT_ACTIVE", "Người ở đã rời khỏi hợp đồng", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (request.getLeftAt().isBefore(tenant.getJoinedAt())) {
                throw new BusinessException("INVALID_DATE",
                        "Ngày rời đi trước ngày bắt đầu ở", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (tenant.isRepresentative()) {
                throw new BusinessException("CANNOT_REMOVE_REPRESENTATIVE", "Không thể rời hợp đồng khi đang là người đại diện", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            long activeOccupants = tenants.countByContractIdAndLeftAtIsNull(locked.getId());
            if (activeOccupants <= 1 && locked.getStatus() == ContractStatus.ACTIVE) {
                throw new BusinessException("LAST_TENANT",
                        "Không thể rời vì là người ở cuối cùng trong hợp đồng ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            tenant.setLeftAt(request.getLeftAt());
            ContractTenant saved = tenants.save(tenant);

            outbox.publishTenantRemoved(new TenantRemovedPayload(
                    locked.getId(), locked.getRoomId(), locked.getBuildingId(), saved.getId(),
                    saved.getCustomerId(), saved.getLeftAt(), nextRevision(locked)));

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
        return contracts.findContractsForCustomer(customerId).stream()
                .map(ContractResponse::from)
                .toList();
    }

    public ContractDetailResponse getContractById(Long id) {
        Contract contract = contracts.findById(id)
                .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + id));

        HeaderUser user = currentUser();
        boolean isStaffOrAdmin = user.getRoles().contains("ADMIN") || user.getRoles().contains("QUAN_LY") || user.getRoles().contains("SALE");

        if (isStaffOrAdmin) {
            buildingScope.assertAssigned(contract.getBuildingId());
        } else {
            assertCanView(contract);
        }

        List<ContractTenant> contractTenants = tenants.findByContractIdOrderByJoinedAtAsc(id);
        List<ContractSignatureResponse> signatureResponses = signatureRows.findByContractIdOrderBySignedAtAsc(id).stream()
                .map(ContractSignatureResponse::from)
                .toList();

        ContractDocumentResponse docResponse = documents.findFirstByContractIdAndDocumentTypeOrderByVersionDesc(id, "CONTRACT")
                .map(ContractDocumentResponse::from)
                .orElse(null);

        return ContractDetailResponse.from(contract, contractTenants, signatureResponses, docResponse);
    }

    public ContractResponse terminate(Long id, TerminateContractRequest request) {
        if (request == null || request.getTerminationDate() == null) {
            throw new BusinessException("VALIDATION_ERROR", "Thiếu terminationDate", HttpStatus.BAD_REQUEST);
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            Contract contract = contracts.findById(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + id));
            buildingScope.assertAssigned(contract.getBuildingId());

            if (contract.getStatus() != ContractStatus.ACTIVE) {
                throw new BusinessException("INVALID_STATUS", "Chỉ chấm dứt hợp đồng ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            validateTerminationDate(request.getTerminationDate(), contract);

            contracts.lockNamedKey(signKey("room", contract.getRoomId()));
            Contract locked = contracts.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + id));

            if (locked.getStatus() != ContractStatus.ACTIVE) {
                throw new BusinessException("INVALID_STATUS", "Chỉ chấm dứt hợp đồng ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            validateTerminationDate(request.getTerminationDate(), locked);

            locked.setEndDate(request.getTerminationDate());
            locked.setTerminationReason(request.getReason() != null ? request.getReason().trim() : "Chấm dứt trước hạn");
            Contract saved = contracts.save(locked);
            return ContractResponse.from(saved);
        });
    }

    private static void validateTerminationDate(LocalDate terminationDate, Contract contract) {
        if (terminationDate.isBefore(contract.getStartDate())) {
            throw new BusinessException("INVALID_DATE", "Ngày chấm dứt không được trước ngày bắt đầu", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (contract.getEndDate() != null && terminationDate.isAfter(contract.getEndDate())) {
            throw new BusinessException("INVALID_DATE", "Ngày chấm dứt không được vượt quá ngày kết thúc hiện tại", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    public ContractResponse terminateContract(Long id, TerminateContractRequest request) {
        return terminate(id, request);
    }

    public List<ContractResponse> getRoommateContracts(Long customerId) {
        return contracts.findContractsForCustomer(customerId).stream()
                .map(ContractResponse::from)
                .toList();
    }

    /** Chat context: hop dong ACTIVE gan nhat cua khach (dai dien hoac nguoi o) de tim quan ly toa. */
    @Transactional(readOnly = true)
    public java.util.Optional<ChatContractContext> findActiveContractForCustomer(Long customerId) {
        return contracts.findActiveForCustomer(customerId, ContractStatus.ACTIVE).stream()
                .findFirst()
                .map(contract -> new ChatContractContext(contract.getId(), contract.getCustomerId(),
                        contract.getBuildingId(), contract.getRoomId(), contract.getRoomNumber(),
                        contract.getBuildingName(), contract.getStatus().name()));
    }

    public MoveOutRequestResponse createMoveOutRequest(CreateMoveOutRequest request) {
        if (request == null || request.getContractId() == null || request.getExpectedMoveOutDate() == null) {
            throw new BusinessException("VALIDATION_ERROR", "Thiếu thông tin yêu cầu trả phòng", HttpStatus.BAD_REQUEST);
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            HeaderUser user = currentUser();
            Long customerId = Long.valueOf(user.getUserId());

            Contract contract = contracts.findById(request.getContractId())
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + request.getContractId()));

            if (contract.getStatus() != ContractStatus.ACTIVE) {
                throw new BusinessException("INVALID_STATUS", "Chỉ tạo yêu cầu trả phòng cho hợp đồng ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            requireRepresentative(contract.getId(), customerId);

            LocalDate today = LocalDate.now(clock);
            validateMoveOutDate(request.getExpectedMoveOutDate(), contract, today);

            contracts.lockNamedKey(signKey("room", contract.getRoomId()));
            Contract locked = contracts.findByIdForUpdate(request.getContractId())
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng"));
            if (locked.getStatus() != ContractStatus.ACTIVE) {
                throw new BusinessException("INVALID_STATUS", "Chỉ tạo yêu cầu trả phòng cho hợp đồng ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            // Both reads happened before the lock, so a concurrent representative swap, a departure, or a
            // renew/terminate that moved end_date could have invalidated them.
            requireRepresentative(locked.getId(), customerId);
            validateMoveOutDate(request.getExpectedMoveOutDate(), locked, today);

            if (moveOutRequests.existsByContractIdAndStatus(locked.getId(), MoveOutStatus.PENDING)) {
                throw new BusinessException("REQUEST_ALREADY_EXISTS", "Đã có yêu cầu trả phòng đang chờ duyệt", HttpStatus.CONFLICT);
            }

            MoveOutRequest mor = new MoveOutRequest();
            mor.setContractId(locked.getId());
            mor.setRequestedBy(customerId);
            mor.setRequestDate(today);
            mor.setExpectedMoveOutDate(request.getExpectedMoveOutDate());
            mor.setReason(request.getReason() != null ? request.getReason() : "");
            mor.setStatus(MoveOutStatus.PENDING);

            MoveOutRequest saved;
            try {
                saved = moveOutRequests.saveAndFlush(mor);
            } catch (DataIntegrityViolationException race) {
                if (violates(race, PENDING_MOVE_OUT_INDEX)) {
                    throw new BusinessException("REQUEST_ALREADY_EXISTS",
                            "Đã có yêu cầu trả phòng đang chờ duyệt", HttpStatus.CONFLICT);
                }
                throw race;
            }

            outbox.publishMoveOutRequested(new MoveOutRequestedPayload(
                    saved.getId(), locked.getId(), locked.getRoomId(), locked.getBuildingId(),
                    saved.getExpectedMoveOutDate()));

            return MoveOutRequestResponse.from(saved);
        });
    }

    private void requireRepresentative(Long contractId, Long customerId) {
        ContractTenant tenant = tenants.findByContractIdAndCustomerIdAndLeftAtIsNull(contractId, customerId)
                .orElseThrow(() -> new BusinessException("NOT_REPRESENTATIVE",
                        "Khách không phải người ở hiện tại", HttpStatus.FORBIDDEN));
        if (!tenant.isRepresentative()) {
            throw new BusinessException("NOT_REPRESENTATIVE",
                    "Chỉ người đại diện mới được gửi yêu cầu trả phòng", HttpStatus.FORBIDDEN);
        }
    }

    private static void validateMoveOutDate(LocalDate expectedMoveOutDate, Contract contract, LocalDate today) {
        if (expectedMoveOutDate.isBefore(today)) {
            throw new BusinessException("INVALID_DATE", "Ngày dự kiến trả phòng phải từ hôm nay", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        validateMoveOutDateAgainstContract(expectedMoveOutDate, contract);
    }

    private static void validateMoveOutDateAgainstContract(LocalDate expectedMoveOutDate, Contract contract) {
        if (expectedMoveOutDate.isBefore(contract.getStartDate())) {
            throw new BusinessException("INVALID_DATE", "Ngày dự kiến trả phòng không được trước ngày bắt đầu", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (contract.getEndDate() != null && expectedMoveOutDate.isAfter(contract.getEndDate())) {
            throw new BusinessException("INVALID_DATE", "Ngày dự kiến trả phòng không được vượt quá ngày kết thúc hiện tại", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    public Page<MoveOutRequestResponse> searchMoveOutRequests(Long buildingId, MoveOutStatus status, boolean unread, Pageable pageable) {
        if (buildingId != null) {
            buildingScope.assertAssigned(buildingId);
        }
        Collection<Long> allowed = buildingScope.getAssignedBuildingIds();
        return moveOutRequests.findByFilters(buildingId, allowed, status, unread, pageable)
                .map(MoveOutRequestResponse::from);
    }

    public MoveOutRequestResponse markMoveOutRead(Long id) {
        MoveOutRequest req = moveOutRequests.findById(id)
                .orElseThrow(() -> new NotFoundException("MOVE_OUT_NOT_FOUND", "Không tìm thấy yêu cầu trả phòng " + id));
        Contract c = contracts.findById(req.getContractId())
                .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng"));
        buildingScope.assertAssigned(c.getBuildingId());
        if (req.getReadAt() == null) {
            req.setReadAt(Instant.now(clock));
            req = moveOutRequests.save(req);
        }
        return MoveOutRequestResponse.from(req);
    }

    public MoveOutRequestResponse approveMoveOutRequest(Long id) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            MoveOutRequest mor = moveOutRequests.findById(id)
                    .orElseThrow(() -> new NotFoundException("MOVE_OUT_NOT_FOUND", "Không tìm thấy yêu cầu trả phòng " + id));
            Contract contract = contracts.findById(mor.getContractId())
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng"));
            buildingScope.assertAssigned(contract.getBuildingId());

            // Lock order stays room advisory -> contract row -> request row, so terminate/renew (which take
            // room + contract) cannot interleave with approve and rewrite end_date from stale data.
            contracts.lockNamedKey(signKey("room", contract.getRoomId()));
            Contract locked = contracts.findByIdForUpdate(contract.getId())
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng"));
            MoveOutRequest lockedMor = moveOutRequests.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("MOVE_OUT_NOT_FOUND", "Không tìm thấy yêu cầu trả phòng " + id));

            if (lockedMor.getStatus() != MoveOutStatus.PENDING) {
                throw new BusinessException("INVALID_STATUS", "Chỉ duyệt yêu cầu ở trạng thái PENDING", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (locked.getStatus() != ContractStatus.ACTIVE) {
                throw new BusinessException("INVALID_STATUS", "Hợp đồng không ở trạng thái ACTIVE", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            validateMoveOutDateAgainstContract(lockedMor.getExpectedMoveOutDate(), locked);

            locked.setEndDate(lockedMor.getExpectedMoveOutDate());
            locked.setRevision(locked.getRevision() + 1);
            Contract savedContract = contracts.save(locked);

            lockedMor.setStatus(MoveOutStatus.APPROVED);
            MoveOutRequest saved = moveOutRequests.save(lockedMor);

            List<Long> tenantIds = tenants.findByContractIdAndLeftAtIsNullOrderByJoinedAtAsc(locked.getId()).stream()
                    .map(ContractTenant::getCustomerId)
                    .toList();

            outbox.publishMoveOutApproved(new MoveOutApprovedPayload(
                    saved.getId(), savedContract.getId(), savedContract.getRoomId(), savedContract.getBuildingId(),
                    saved.getExpectedMoveOutDate(), tenantIds, savedContract.getRevision()));

            return MoveOutRequestResponse.from(saved);
        });
    }

    public MoveOutRequestResponse rejectMoveOutRequest(Long id, RejectMoveOutRequest request) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            MoveOutRequest mor = moveOutRequests.findById(id)
                    .orElseThrow(() -> new NotFoundException("MOVE_OUT_NOT_FOUND", "Không tìm thấy yêu cầu trả phòng " + id));
            Contract contract = contracts.findById(mor.getContractId())
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng"));
            buildingScope.assertAssigned(contract.getBuildingId());

            MoveOutRequest lockedMor = moveOutRequests.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("MOVE_OUT_NOT_FOUND", "Không tìm thấy yêu cầu trả phòng " + id));

            if (lockedMor.getStatus() != MoveOutStatus.PENDING) {
                throw new BusinessException("INVALID_STATUS", "Chỉ từ chối yêu cầu ở trạng thái PENDING", HttpStatus.UNPROCESSABLE_ENTITY);
            }

            if (request != null && request.getReason() != null && !request.getReason().isBlank()) {
                lockedMor.setReason(request.getReason());
            }
            lockedMor.setStatus(MoveOutStatus.REJECTED);
            MoveOutRequest saved = moveOutRequests.save(lockedMor);
            return MoveOutRequestResponse.from(saved);
        });
    }

    public String uploadMoveOutRefundReceipt(Long moveOutId, MultipartFile file) {
        MoveOutRequest mor = moveOutRequests.findById(moveOutId)
                .orElseThrow(() -> new NotFoundException("MOVE_OUT_NOT_FOUND", "Không tìm thấy yêu cầu trả phòng " + moveOutId));
        Contract contract = contracts.findById(mor.getContractId())
                .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng"));
        buildingScope.assertAssigned(contract.getBuildingId());

        String key = documentStorage.uploadMoveOutRefundReceipt(moveOutId, file);
        mor.setRefundReceiptImageKey(key);
        moveOutRequests.save(mor);
        return key;
    }

    public MoveOutRequestResponse settleMoveOutRefund(Long moveOutId, SettleMoveOutRefundRequest request) {
        MoveOutRequest mor = moveOutRequests.findById(moveOutId)
                .orElseThrow(() -> new NotFoundException("MOVE_OUT_NOT_FOUND", "Không tìm thấy yêu cầu trả phòng " + moveOutId));
        Contract contract = contracts.findById(mor.getContractId())
                .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng"));
        buildingScope.assertAssigned(contract.getBuildingId());

        if (mor.getStatus() != MoveOutStatus.APPROVED && mor.getStatus() != MoveOutStatus.COMPLETED) {
            throw new BusinessException("INVALID_STATUS", "Chỉ quyết toán cọc cho yêu cầu đã được duyệt hoặc hoàn tất", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        BigDecimal refundAmt = (request != null && request.refundAmount() != null)
                ? request.refundAmount()
                : (contract.getDepositAmount() != null ? contract.getDepositAmount() : BigDecimal.ZERO);

        mor.setRefundAmount(refundAmt);
        mor.setDeductionAmount(request != null ? request.deductionAmount() : null);
        mor.setDeductionReason(request != null ? request.deductionReason() : null);
        mor.setRefundStatus(request != null && request.refundStatus() != null ? request.refundStatus() : "COMPLETED");
        mor.setStatus(MoveOutStatus.COMPLETED);
        mor.setRefundedAt(Instant.now(clock));
        MoveOutRequest saved = moveOutRequests.save(mor);

        return MoveOutRequestResponse.from(saved);
    }

    public ContractDocumentStorage.Download openMoveOutRefundReceipt(Long moveOutId) {
        MoveOutRequest mor = moveOutRequests.findById(moveOutId)
                .orElseThrow(() -> new NotFoundException("MOVE_OUT_NOT_FOUND", "Không tìm thấy yêu cầu trả phòng " + moveOutId));
        Contract contract = contracts.findById(mor.getContractId())
                .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng"));
        assertCanView(contract);

        if (mor.getRefundReceiptImageKey() == null) {
            throw new NotFoundException("RECEIPT_NOT_FOUND", "Chưa có ảnh biên lai hoàn cọc cho yêu cầu này");
        }
        return documentStorage.open(mor.getRefundReceiptImageKey());
    }

    public List<MoveOutRequestResponse> findMyMoveOutRequests() {
        HeaderUser user = currentUser();
        Long customerId = Long.valueOf(user.getUserId());
        return moveOutRequests.findByRequestedByOrderByCreatedAtDesc(customerId).stream()
                .map(MoveOutRequestResponse::from)
                .toList();
    }

    public List<BillingContractData> getBillingData(int month, int year) {
        YearMonth ym = YearMonth.of(year, month);
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        List<Contract> effectiveContracts = contracts.findContractsEffectiveInMonth(
                monthStart, monthEnd, List.of(ContractStatus.ACTIVE, ContractStatus.ENDED, ContractStatus.TERMINATED));

        return effectiveContracts.stream().map(c -> {
            List<ContractTenant> contractTenants = tenants.findByContractIdOrderByJoinedAtAsc(c.getId());
            List<BillingContractData.BillingTenantData> tenantDataList = contractTenants.stream()
                    .map(t -> new BillingContractData.BillingTenantData(
                            t.getId(), t.getCustomerId(), t.getJoinedAt(), t.getLeftAt(), t.isRepresentative()))
                    .toList();
            return new BillingContractData(
                    c.getId(), c.getRoomId(), c.getBuildingId(), c.getRoomNumber(), c.getCustomerId(),
                    c.getStatus().name(), c.getStartDate(), c.getEndDate(), c.getRentAmount(),
                    c.getPaymentDueDay(), tenantDataList);
        }).toList();
    }

    public RoomTenantCheckResponse checkRoomTenant(Long roomId, Long customerId) {
        Optional<Contract> activeContract =
                contracts.findFirstByRoomIdAndStatusOrderByStartDateDesc(roomId, ContractStatus.ACTIVE);
        if (activeContract.isEmpty()) {
            return new RoomTenantCheckResponse(false, null, null);
        }
        Contract c = activeContract.get();
        boolean isTenant = Objects.equals(c.getCustomerId(), customerId)
                || tenants.existsByContractIdAndCustomerIdAndLeftAtIsNull(c.getId(), customerId);
        return new RoomTenantCheckResponse(isTenant, isTenant ? c.getId() : null, isTenant ? c.getBuildingId() : null);
    }

    public InternalContractDepositResponse getDepositInfo(Long contractId) {
        Contract contract = contracts.findById(contractId)
                .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + contractId));
        return toInternalDepositResponse(contract);
    }

    public InternalContractDepositResponse markDepositPaid(Long contractId, MarkDepositPaidRequest request) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            Contract locked = contracts.findByIdForUpdate(contractId)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + contractId));
            if (locked.isDepositPaid()) {
                return toInternalDepositResponse(locked);
            }
            if (locked.getStatus() == ContractStatus.ENDED || locked.getStatus() == ContractStatus.TERMINATED) {
                throw new BusinessException("INVALID_STATUS",
                        "Hợp đồng không ở trạng thái hợp lệ để thanh toán cọc", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            String method = (request.getMethod() != null && !request.getMethod().isBlank())
                    ? request.getMethod().trim()
                    : "VNPAY";
            locked.setDepositPaid(true);
            locked.setDepositPaidAt(Instant.now(clock));
            locked.setDepositPaymentMethod(method);
            locked.setDepositTxnRef(request.getTxnRef());
            Contract saved = contracts.save(locked);
            outbox.publishContractDepositPaid(saved);
            return toInternalDepositResponse(saved);
        });
    }

    private static InternalContractDepositResponse toInternalDepositResponse(Contract c) {
        return new InternalContractDepositResponse(
                c.getId(),
                c.getCustomerId(),
                c.getBuildingId(),
                c.getRoomId(),
                c.getRoomNumber(),
                c.getStatus().name(),
                c.getSignatureStatus().name(),
                c.getDepositAmount(),
                c.isDepositPaid(),
                c.getDepositTxnRef()
        );
    }

    public void processRoommateAccepted(RoommateApplicationAcceptedEvent event) {
        Long roomId = event.getRoomId();
        Long buildingId = event.getBuildingId();
        Long applicationId = event.getApplicationId();
        Long ownerCustomerId = event.getOwnerCustomerId();
        Long applicantCustomerId = event.getApplicantCustomerId();

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            Optional<Contract> activeContractOpt = contracts.findFirstByRoomIdAndStatusOrderByStartDateDesc(roomId, ContractStatus.ACTIVE);
            if (activeContractOpt.isEmpty()) {
                outbox.publishTenantAddRejected(new TenantAddRejectedPayload(
                        roomId, buildingId, applicationId, ownerCustomerId, applicantCustomerId, "NO_ACTIVE_CONTRACT"
                ));
                return;
            }

            Contract contract = activeContractOpt.get();

            contracts.lockNamedKey(signKey("customer", applicantCustomerId));
            contracts.lockNamedKey(signKey("room", contract.getRoomId()));

            Contract locked = contracts.findByIdForUpdate(contract.getId()).orElse(null);
            if (locked == null || locked.getStatus() != ContractStatus.ACTIVE) {
                outbox.publishTenantAddRejected(new TenantAddRejectedPayload(
                        roomId, buildingId, applicationId, ownerCustomerId, applicantCustomerId, "NO_ACTIVE_CONTRACT"
                ));
                return;
            }

            if (tenants.existsByContractIdAndCustomerIdAndLeftAtIsNull(locked.getId(), applicantCustomerId) ||
                    tenants.existsCurrentTenantInContractWithStatus(applicantCustomerId, ContractStatus.ACTIVE)) {
                outbox.publishTenantAddRejected(new TenantAddRejectedPayload(
                        roomId, buildingId, applicationId, ownerCustomerId, applicantCustomerId, "ALREADY_IN_ROOM"
                ));
                return;
            }

            Integer maxCapacity;
            try {
                PropertyClient.RoomInfo room = properties.getRoom(locked.getRoomId());
                maxCapacity = room == null ? null : room.maxCapacity();
            } catch (Exception unavailable) {
                throw new BusinessException("DEPENDENCY_UNAVAILABLE", "Không kiểm tra được sức chứa phòng", HttpStatus.SERVICE_UNAVAILABLE);
            }
            if (maxCapacity == null || maxCapacity < 1) {
                throw new BusinessException("DEPENDENCY_UNAVAILABLE", "Không kiểm tra được sức chứa phòng", HttpStatus.SERVICE_UNAVAILABLE);
            }

            long currentOccupancy = tenants.countByContractIdAndLeftAtIsNull(locked.getId());
            if (currentOccupancy + 1 > maxCapacity) {
                outbox.publishTenantAddRejected(new TenantAddRejectedPayload(
                        roomId, buildingId, applicationId, ownerCustomerId, applicantCustomerId, "ROOM_FULL"
                ));
                return;
            }

            ContractTenant tenant = new ContractTenant();
            tenant.setContractId(locked.getId());
            tenant.setCustomerId(applicantCustomerId);
            tenant.setJoinedAt(event.getMoveInDate() != null ? event.getMoveInDate() : LocalDate.now(clock));
            tenant.setLeftAt(null);
            tenant.setRepresentative(false);
            tenant.setFullName(event.getFullName());
            tenant.setPhone(event.getPhone());
            tenant.setCccdNumber(event.getCccdNumber());
            ContractTenant savedTenant = tenants.save(tenant);

            try {
                List<ContractTenant> members = new java.util.ArrayList<>(
                        tenants.findByContractIdOrderByJoinedAtAsc(locked.getId()));
                members.add(savedTenant);
                documentService.generateAddendum(locked, members, savedTenant);
            } catch (Exception ex) {
                log.warn("Không thể sinh phụ lục thêm người ở cho hợp đồng {}: {}",
                        locked.getId(), ex.getMessage());
            }

            outbox.publishTenantAdded(new TenantAddedPayload(
                    locked.getId(), locked.getRoomId(), locked.getBuildingId(), savedTenant.getId(),
                    applicantCustomerId, savedTenant.getJoinedAt(), applicationId, ownerCustomerId,
                    nextRevision(locked)));
        });
    }

    public ContractResponse sign(Long id) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return template.execute(status -> {
            Contract contract = contracts.findById(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + id));
            buildingScope.assertAssigned(contract.getBuildingId());

            if (contract.getStatus() != ContractStatus.DRAFT) {
                throw new BusinessException("INVALID_STATUS", "Chỉ ký hợp đồng ở trạng thái DRAFT", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            validateSigningFields(contract);

            contracts.lockNamedKey(signKey("customer", contract.getCustomerId()));
            contracts.lockNamedKey(signKey("room", contract.getRoomId()));
            Contract locked = contracts.findByIdForUpdate(id)
                    .orElseThrow(() -> new NotFoundException("CONTRACT_NOT_FOUND", "Không tìm thấy hợp đồng " + id));

            if (locked.getStatus() != ContractStatus.DRAFT) {
                throw new BusinessException("INVALID_STATUS", "Chỉ ký hợp đồng ở trạng thái DRAFT", HttpStatus.UNPROCESSABLE_ENTITY);
            }
            validateSigningFields(locked);

            if (contracts.existsActiveOverlappingContract(locked.getRoomId(), ContractStatus.ACTIVE,
                    locked.getStartDate(), locked.getEndDate(), locked.getId())) {
                throw new BusinessException("ROOM_ALREADY_CONTRACTED",
                        "Phòng đã có hợp đồng ACTIVE trùng khoảng thời gian", HttpStatus.CONFLICT);
            }
            if (tenants.existsCurrentTenantInContractWithStatus(locked.getCustomerId(), ContractStatus.ACTIVE)) {
                throw new BusinessException("CUSTOMER_ALREADY_IN_ROOM",
                        "Khách đã thuộc một hợp đồng ACTIVE khác", HttpStatus.CONFLICT);
            }

            locked.setStatus(ContractStatus.ACTIVE);
            Contract saved = contracts.save(locked);

            ContractTenant representative;
            try {
                representative = tenants.save(initialRepresentative(saved));
            } catch (DataIntegrityViolationException race) {
                if (violates(race, DUPLICATE_CUSTOMER_INDEX) || violates(race, REPRESENTATIVE_INDEX)) {
                    throw new BusinessException("CUSTOMER_ALREADY_IN_ROOM",
                            "Khách đã thuộc một hợp đồng ACTIVE khác", HttpStatus.CONFLICT);
                }
                throw race;
            }

            // Activation and the representative insert are two projection events (contract status,
            // then tenant added); each one needs its own revision so the Property projection does not
            // silently drop the second event.
            saved.setRevision(saved.getRevision() + 1);
            outbox.publishContractSigned(saved, List.of(saved.getCustomerId()));
            saved.setRevision(saved.getRevision() + 1);
            saved = contracts.save(saved);
            outbox.publishTenantAdded(new TenantAddedPayload(
                    saved.getId(), saved.getRoomId(), saved.getBuildingId(), representative.getId(),
                    representative.getCustomerId(), representative.getJoinedAt(), null,
                    saved.getCustomerId(), saved.getRevision()));
            try {
                documentService.generate(saved, pdfTenants(saved));
            } catch (Exception ex) {
                log.warn("Không thể sinh PDF sau khi ký: {}", ex.getMessage());
            }
            return ContractResponse.from(saved);
        });
    }

    private static void validateSigningFields(Contract contract) {
        if (contract.getStartDate() == null || contract.getEndDate() == null) {
            throw new BusinessException("CONTRACT_INCOMPLETE",
                    "Hợp đồng thiếu ngày bắt đầu hoặc ngày kết thúc", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!contract.getEndDate().isAfter(contract.getStartDate())) {
            throw new BusinessException("INVALID_DATE", "Ngày kết thúc phải sau ngày bắt đầu", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (contract.getRentAmount() == null || contract.getRentAmount().signum() <= 0) {
            throw new BusinessException("CONTRACT_INCOMPLETE", "Hợp đồng thiếu giá thuê hợp lệ", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * The Property projection requires every contract event revision to be exactly the previous one
     * plus one, so each outbox event advances and persists the contract revision before publishing.
     */
    private long nextRevision(Contract contract) {
        long revision = contract.getRevision() + 1;
        contract.setRevision(revision);
        contracts.save(contract);
        return revision;
    }

    private List<ContractTenant> pdfTenants(Contract contract) {
        List<ContractTenant> list = tenants.findByContractIdOrderByJoinedAtAsc(contract.getId());
        if (list.isEmpty() && contract.getRenewedFromContractId() != null) {
            return tenants.findByContractIdOrderByJoinedAtAsc(contract.getRenewedFromContractId());
        }
        return list;
    }

    private static String signKey(String domain, Long id) {
        return domain + ":" + id;
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

    private static BigDecimal normalizeMoney(BigDecimal value) {
        return value == null ? null : value.setScale(0, java.math.RoundingMode.HALF_UP);
    }

    private static ContractTenant initialRepresentative(Contract contract) {
        ContractTenant tenant = new ContractTenant();
        tenant.setContractId(contract.getId());
        tenant.setCustomerId(contract.getCustomerId());
        tenant.setJoinedAt(contract.getStartDate());
        tenant.setLeftAt(null);
        tenant.setRepresentative(true);
        tenant.setFullName(contract.getTenantFullName());
        tenant.setDateOfBirth(contract.getTenantDateOfBirth());
        tenant.setGender(contract.getTenantGender());
        tenant.setPhone(contract.getTenantPhone());
        tenant.setEmail(contract.getTenantEmail());
        tenant.setPermanentAddress(contract.getTenantPermanentAddress());
        tenant.setOccupation(contract.getTenantOccupation());
        tenant.setCccdNumber(contract.getTenantCccdNumber());
        tenant.setCccdIssueDate(contract.getTenantCccdIssueDate());
        tenant.setCccdIssuePlace(contract.getTenantCccdIssuePlace());
        tenant.setCccdFrontKey(contract.getTenantCccdFrontKey());
        tenant.setCccdBackKey(contract.getTenantCccdBackKey());
        return tenant;
    }

    private void assertCanView(Contract contract) {
        HeaderUser user = currentUser();
        boolean isStaffOrAdmin = user.getRoles().contains("ADMIN") || user.getRoles().contains("QUAN_LY") || user.getRoles().contains("SALE");
        if (isStaffOrAdmin) {
            buildingScope.assertAssigned(contract.getBuildingId());
            return;
        }
        Long customerId = Long.valueOf(user.getUserId());
        if (Objects.equals(contract.getCustomerId(), customerId)) {
            return;
        }
        if (tenants.existsByContractIdAndCustomerIdAndLeftAtIsNull(contract.getId(), customerId)) {
            return;
        }
        throw new AccessDeniedException("Bạn không có quyền truy cập hợp đồng này");
    }

    private static void assertTenantOwnsContract(Contract contract, Long customerId) {
        if (!Objects.equals(contract.getCustomerId(), customerId)) {
            throw new AccessDeniedException("Bạn không phải người đứng tên hợp đồng này");
        }
    }

    private HeaderUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HeaderUser hu) {
            return hu;
        }
        throw new AccessDeniedException("Chưa đăng nhập");
    }
}