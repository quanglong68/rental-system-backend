package com.rental.listingbooking.service;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.error.NotFoundException;
import com.rental.common.dto.PageResponse;
import com.rental.listingbooking.dto.RejectRentalRequestDto;
import com.rental.listingbooking.dto.RentalRequestCreateDto;
import com.rental.listingbooking.dto.RentalRequestResponse;
import com.rental.listingbooking.entity.RentalRequest;
import com.rental.listingbooking.entity.RoomListing;
import com.rental.listingbooking.outbox.ListingEventPublisher;
import com.rental.listingbooking.outbox.RentalRequestAcceptedPayload;
import com.rental.listingbooking.outbox.RentalRequestCreatedPayload;
import com.rental.listingbooking.outbox.RentalRequestRejectedPayload;
import com.rental.listingbooking.repository.RentalRequestRepository;
import com.rental.listingbooking.repository.RoomListingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RentalRequestService {

    private static final String PENDING = "PENDING";
    private static final String VERIFIED = "VERIFIED";
    private static final String ACCEPTED = "ACCEPTED";
    private static final String REJECTED = "REJECTED";
    private static final String CANCELLED = "CANCELLED";
    private static final List<String> OPEN_STATUSES = List.of(PENDING, VERIFIED);
    private static final String CUSTOMER_TYPE = "CUSTOMER";

    private final RentalRequestRepository requestRepository;
    private final RoomListingRepository listingRepository;
    private final ListingEventPublisher publisher;
    private final BuildingScope buildingScope;
    private final IdentityFileStorage identityFileStorage;
    private final int maxMoveInDays;

    public RentalRequestService(RentalRequestRepository requestRepository,
                                RoomListingRepository listingRepository,
                                ListingEventPublisher publisher,
                                BuildingScope buildingScope,
                                IdentityFileStorage identityFileStorage,
                                @Value("${booking.max-move-in-days:30}") int maxMoveInDays) {
        this.requestRepository = requestRepository;
        this.listingRepository = listingRepository;
        this.publisher = publisher;
        this.buildingScope = buildingScope;
        this.identityFileStorage = identityFileStorage;
        this.maxMoveInDays = maxMoveInDays;
    }

    @Transactional
    public RentalRequestResponse createRequest(RentalRequestCreateDto dto, Long customerId) {
        RoomListing listing = listingRepository.findById(dto.listingId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tin đăng ID " + dto.listingId()));

        if (!"ACTIVE".equals(listing.getStatus())) {
            throw new BusinessException("LISTING_NOT_ACTIVE",
                    "Tin đăng phòng hiện không còn nhận yêu cầu", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        if (!dto.termsAccepted()) {
            throw new BusinessException("TERMS_NOT_ACCEPTED",
                    "Vui lòng đọc và đồng ý với điều khoản hợp đồng trước khi gửi yêu cầu",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        assertListingTermsReady(listing);

        String expectedPrefix = "identity/" + customerId + "/";
        if (!dto.cccdFrontKey().startsWith(expectedPrefix) || !dto.cccdBackKey().startsWith(expectedPrefix)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Ảnh CCCD không hợp lệ, vui lòng tải lại ảnh");
        }

        requestRepository.findByCustomerIdAndListingIdAndStatusIn(customerId, dto.listingId(), OPEN_STATUSES)
                .stream().findFirst().ifPresent(existing -> {
                    throw new BusinessException("REQUEST_ALREADY_EXISTS",
                            "Bạn đã gửi yêu cầu thuê cho phòng này và đang được xử lý. "
                                    + "Vui lòng chờ kết quả hoặc hủy yêu cầu cũ trước khi gửi lại.",
                            HttpStatus.CONFLICT);
                });

        validateMoveInDate(dto.moveInDate(), listing);

        RentalRequest request = new RentalRequest();
        request.setCustomerId(customerId);
        request.setListingId(listing.getId());
        request.setRoomId(listing.getRoomId());
        request.setBuildingId(listing.getBuildingId());
        request.setStatus(PENDING);
        request.setMoveInDate(dto.moveInDate());
        request.setNote(dto.note());
        request.setFullName(dto.fullName().trim());
        request.setDateOfBirth(dto.dateOfBirth());
        request.setGender(dto.gender());
        request.setPhone(dto.phone().trim());
        request.setEmail(dto.email());
        request.setPermanentAddress(dto.permanentAddress().trim());
        request.setOccupation(dto.occupation());
        request.setCccdNumber(dto.cccdNumber());
        request.setCccdIssueDate(dto.cccdIssueDate());
        request.setCccdIssuePlace(dto.cccdIssuePlace().trim());
        request.setCccdFrontKey(dto.cccdFrontKey());
        request.setCccdBackKey(dto.cccdBackKey());
        request.setTermsAccepted(true);
        snapshotTerms(request, listing);
        request.setRequestedAt(Instant.now());

        RentalRequest saved = requestRepository.save(request);

        publisher.publishRentalRequestCreated(new RentalRequestCreatedPayload(
                saved.getId(),
                saved.getListingId(),
                saved.getRoomId(),
                saved.getBuildingId(),
                saved.getCustomerId()
        ));

        return toResponse(saved);
    }

    /** Snapshot điều khoản + bên A để hợp đồng không đổi khi tin đăng/tòa nhà sửa về sau. */
    private static void snapshotTerms(RentalRequest request, RoomListing listing) {
        request.setRentAmount(listing.getPrice());
        request.setDepositAmount(listing.getDepositAmount());
        request.setElectricityPrice(listing.getElectricityPrice());
        request.setWaterPrice(listing.getWaterPrice());
        request.setWaterUnit(listing.getWaterUnit());
        request.setPaymentMethod(listing.getPaymentMethod());
        request.setPaymentDueDay(listing.getPaymentDueDay());
        request.setMinTermMonths(listing.getMinTermMonths());
        request.setAvailableFrom(listing.getAvailableFrom());
        request.setLessorName(listing.getLessorName());
        request.setLessorDateOfBirth(listing.getLessorDateOfBirth());
        request.setLessorIdNumber(listing.getLessorIdNumber());
        request.setLessorIdIssueDate(listing.getLessorIdIssueDate());
        request.setLessorIdIssuePlace(listing.getLessorIdIssuePlace());
        request.setLessorPhone(listing.getLessorPhone());
        request.setLessorAddress(listing.getLessorAddress());
    }

    private static void assertListingTermsReady(RoomListing listing) {
        if (listing.getDepositAmount() == null || listing.getElectricityPrice() == null
                || listing.getWaterPrice() == null || listing.getWaterUnit() == null
                || listing.getPaymentMethod() == null || listing.getPaymentDueDay() == null
                || listing.getMinTermMonths() == null) {
            throw new BusinessException("LISTING_TERMS_INCOMPLETE",
                    "Tin đăng chưa có đủ điều khoản hợp đồng (cọc, điện, nước, hình thức trả, kỳ hạn). "
                            + "Vui lòng liên hệ quản lý tòa nhà để cập nhật trước khi gửi yêu cầu thuê.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private void validateMoveInDate(LocalDate moveInDate, RoomListing listing) {
        LocalDate today = LocalDate.now();
        LocalDate base = listing.getAvailableFrom() != null ? listing.getAvailableFrom() : today;
        LocalDate earliest = base.isAfter(today.plusDays(1)) ? base : today.plusDays(1);
        LocalDate latest = base.plusDays(maxMoveInDays);
        if (moveInDate.isBefore(earliest) || moveInDate.isAfter(latest)) {
            throw new BusinessException("INVALID_MOVE_IN_DATE",
                    "Ngày dự kiến vào ở phải từ %s đến %s (trong vòng %d ngày kể từ ngày phòng có thể chuyển vào)"
                            .formatted(formatDate(earliest), formatDate(latest), maxMoveInDays),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private static String formatDate(LocalDate date) {
        return "%02d/%02d/%d".formatted(date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }

    public IdentityUpload uploadIdentityFile(org.springframework.web.multipart.MultipartFile file,
                                             String side, Long customerId) {
        String normalized = side == null ? "" : side.trim().toUpperCase(Locale.ROOT);
        if (!"FRONT".equals(normalized) && !"BACK".equals(normalized)) {
            throw new BusinessException("INVALID_IDENTITY_SIDE",
                    "Mặt CCCD phải là FRONT hoặc BACK", HttpStatus.BAD_REQUEST);
        }
        String objectKey = identityFileStorage.upload(customerId, normalized, file);
        return new IdentityUpload(normalized, objectKey);
    }

    public record IdentityUpload(String side, String objectKey) {}

    /**
     * Tải ảnh CCCD: chỉ chủ hồ sơ hoặc nhân viên được phân công tòa nhà của yêu cầu.
     */
    @Transactional(readOnly = true)
    public IdentityFileStorage.Download openIdentityFile(Long id, String side, Long userId, String userType) {
        RentalRequest request = findRequest(id);
        if (CUSTOMER_TYPE.equals(userType)) {
            if (!request.getCustomerId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem ảnh CCCD của hồ sơ này");
            }
        } else {
            buildingScope.assertAssigned(request.getBuildingId());
        }
        String normalized = side == null ? "" : side.trim().toUpperCase(Locale.ROOT);
        String objectKey = switch (normalized) {
            case "FRONT" -> request.getCccdFrontKey();
            case "BACK" -> request.getCccdBackKey();
            default -> throw new BusinessException("INVALID_IDENTITY_SIDE",
                    "Mặt CCCD phải là FRONT hoặc BACK", HttpStatus.BAD_REQUEST);
        };
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Hồ sơ chưa có ảnh CCCD này");
        }
        return identityFileStorage.open(objectKey);
    }

    @Transactional(readOnly = true)
    public PageResponse<RentalRequestResponse> getMyRequests(Long customerId, String status, Pageable pageable) {
        String normalized = normalizeStatusFilter(status);
        Page<RentalRequest> page = normalized != null
                ? requestRepository.findByCustomerIdAndStatus(customerId, normalized, pageable)
                : requestRepository.findByCustomerId(customerId, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<RentalRequestResponse> getManageRequests(
            Long buildingId, Long listingId, String status, boolean unread, Pageable pageable) {
        String normalized = normalizeStatusFilter(status);
        List<Long> allowedBuildings = buildingScope.allowedBuildingIdsOrNull();

        if (allowedBuildings != null) {
            if (buildingId != null && !allowedBuildings.contains(buildingId)) {
                throw new AccessDeniedException("Nhân viên chưa được phân công tòa nhà này");
            }
            if (buildingId == null) {
                if (allowedBuildings.isEmpty()) {
                    return PageResponse.of(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0);
                }
                return PageResponse.from(requestRepository
                        .findManageIn(allowedBuildings, listingId, normalized, unread, pageable)
                        .map(this::toResponse));
            }
        }

        return PageResponse.from(requestRepository
                .findManage(buildingId, listingId, normalized, unread, pageable)
                .map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public RentalRequestResponse getRequestById(Long id, Long userId, String userType) {
        RentalRequest request = findRequest(id);
        if (CUSTOMER_TYPE.equals(userType)) {
            if (!request.getCustomerId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem yêu cầu thuê này");
            }
        } else {
            buildingScope.assertAssigned(request.getBuildingId());
        }
        return toResponse(request);
    }

    @Transactional
    public void cancelRequest(Long id, Long customerId) {
        RentalRequest request = findRequest(id);

        if (!request.getCustomerId().equals(customerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền hủy yêu cầu này");
        }

        if (!OPEN_STATUSES.contains(request.getStatus())) {
            throw new BusinessException("INVALID_STATUS",
                    "Chỉ có thể hủy yêu cầu đang chờ xử lý hoặc đã xác minh hồ sơ",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        request.setStatus(CANCELLED);
        requestRepository.save(request);
    }

    @Transactional
    public RentalRequestResponse verifyRequest(Long id, Long staffId) {
        RentalRequest request = findRequest(id);
        buildingScope.assertAssigned(request.getBuildingId());

        if (!PENDING.equals(request.getStatus())) {
            throw new BusinessException("INVALID_STATUS",
                    "Chỉ có thể xác minh hồ sơ của yêu cầu đang chờ xử lý", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        request.setStatus(VERIFIED);
        request.setVerifiedBy(staffId);
        request.setVerifiedAt(Instant.now());
        RentalRequest saved = requestRepository.save(request);
        return toResponse(saved);
    }

    @Transactional
    public RentalRequestResponse acceptRequest(Long id) {
        RentalRequest request = findRequest(id);
        buildingScope.assertAssigned(request.getBuildingId());

        if (!VERIFIED.equals(request.getStatus())) {
            throw new BusinessException("INVALID_STATUS",
                    "Hồ sơ cần được xác minh trước khi duyệt yêu cầu thuê", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        RoomListing listing = listingRepository.findById(request.getListingId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tin đăng ID " + request.getListingId()));
        if (!"ACTIVE".equals(listing.getStatus())) {
            throw new BusinessException("LISTING_NOT_ACTIVE",
                    "Tin đăng đã đóng nên không thể duyệt yêu cầu", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        request.setStatus(ACCEPTED);
        RentalRequest saved = requestRepository.save(request);

        publisher.publishRentalRequestAccepted(new RentalRequestAcceptedPayload(
                saved.getId(),
                saved.getRoomId(),
                saved.getBuildingId(),
                saved.getCustomerId(),
                listing.getPrice(),
                saved.getMoveInDate(),
                firstNonNull(saved.getDepositAmount(), listing.getDepositAmount()),
                firstNonNull(saved.getMinTermMonths(), listing.getMinTermMonths()),
                listing.getRoomNumber(),
                listing.getBuildingName(),
                listing.getAddress(),
                firstNonNull(saved.getElectricityPrice(), listing.getElectricityPrice()),
                firstNonNull(saved.getWaterPrice(), listing.getWaterPrice()),
                firstNonNull(saved.getWaterUnit(), listing.getWaterUnit()),
                firstNonNull(saved.getPaymentMethod(), listing.getPaymentMethod()),
                firstNonNull(saved.getPaymentDueDay(), listing.getPaymentDueDay()),
                firstNonNull(saved.getAvailableFrom(), listing.getAvailableFrom()),
                firstNonNull(saved.getLessorName(), listing.getLessorName()),
                firstNonNull(saved.getLessorDateOfBirth(), listing.getLessorDateOfBirth()),
                firstNonNull(saved.getLessorIdNumber(), listing.getLessorIdNumber()),
                firstNonNull(saved.getLessorIdIssueDate(), listing.getLessorIdIssueDate()),
                firstNonNull(saved.getLessorIdIssuePlace(), listing.getLessorIdIssuePlace()),
                firstNonNull(saved.getLessorPhone(), listing.getLessorPhone()),
                firstNonNull(saved.getLessorAddress(), listing.getLessorAddress()),
                saved.getFullName(),
                saved.getDateOfBirth(),
                saved.getGender(),
                saved.getPhone(),
                saved.getEmail(),
                saved.getPermanentAddress(),
                saved.getOccupation(),
                saved.getCccdNumber(),
                saved.getCccdIssueDate(),
                saved.getCccdIssuePlace(),
                saved.getCccdFrontKey(),
                saved.getCccdBackKey()
        ));

        // Các yêu cầu khác chỉ bị từ chối khi hợp đồng đã có hiệu lực
        // (contract-tenant-service phát ContractSigned và ContractEventConsumer xử lý).
        return toResponse(saved);
    }

    /** Snapshot on the request wins; the listing is the fallback for requests created before the snapshot existed. */
    private static <T> T firstNonNull(T preferred, T fallback) {
        return preferred != null ? preferred : fallback;
    }

    @Transactional
    public RentalRequestResponse rejectRequest(Long id, RejectRentalRequestDto dto) {
        RentalRequest request = findRequest(id);
        buildingScope.assertAssigned(request.getBuildingId());

        if (!OPEN_STATUSES.contains(request.getStatus())) {
            throw new BusinessException("INVALID_STATUS",
                    "Chỉ có thể từ chối yêu cầu đang chờ xử lý hoặc đã xác minh hồ sơ",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        request.setStatus(REJECTED);
        request.setRejectReason(dto.reason().trim());
        RentalRequest saved = requestRepository.save(request);

        publisher.publishRentalRequestRejected(new RentalRequestRejectedPayload(
                saved.getId(),
                saved.getCustomerId(),
                saved.getRejectReason()
        ));

        return toResponse(saved);
    }

    @Transactional
    public RentalRequestResponse markRead(Long id) {
        RentalRequest request = findRequest(id);
        buildingScope.assertAssigned(request.getBuildingId());
        if (request.getReadAt() == null) {
            request.setReadAt(Instant.now());
            requestRepository.save(request);
        }
        return toResponse(request);
    }

    private RentalRequest findRequest(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy yêu cầu thuê ID " + id));
    }

    private static String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private RentalRequestResponse toResponse(RentalRequest r) {
        return new RentalRequestResponse(
                r.getId(),
                r.getCustomerId(),
                r.getListingId(),
                r.getRoomId(),
                r.getBuildingId(),
                r.getStatus(),
                r.getMoveInDate(),
                r.getNote(),
                r.getRejectReason(),
                r.getFullName(),
                r.getDateOfBirth(),
                r.getGender(),
                r.getPhone(),
                r.getEmail(),
                r.getPermanentAddress(),
                r.getOccupation(),
                r.getCccdNumber(),
                r.getCccdIssueDate(),
                r.getCccdIssuePlace(),
                r.getCccdFrontKey() != null,
                r.getCccdBackKey() != null,
                r.isTermsAccepted(),
                r.getRentAmount(),
                r.getDepositAmount(),
                r.getElectricityPrice(),
                r.getWaterPrice(),
                r.getWaterUnit(),
                r.getPaymentMethod(),
                r.getPaymentDueDay(),
                r.getMinTermMonths(),
                r.getAvailableFrom(),
                r.getLessorName(),
                r.getLessorPhone(),
                r.getLessorAddress(),
                r.getRequestedAt(),
                r.getVerifiedBy(),
                r.getVerifiedAt(),
                r.getReadAt()
        );
    }
}
