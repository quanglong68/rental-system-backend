package com.rental.listingbooking.service;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.error.NotFoundException;
import com.rental.listingbooking.client.ContractClient;
import com.rental.listingbooking.client.UserClient;
import com.rental.common.dto.PageResponse;
import com.rental.listingbooking.dto.RoommateApplicationCreateDto;
import com.rental.listingbooking.dto.RoommateApplicationResponse;
import com.rental.listingbooking.dto.RoommatePostCreateDto;
import com.rental.listingbooking.dto.RoommatePostResponse;
import com.rental.listingbooking.entity.RoommateApplication;
import com.rental.listingbooking.entity.RoommatePost;
import com.rental.listingbooking.outbox.ListingEventPublisher;
import com.rental.listingbooking.outbox.RoommateApplicationAcceptedPayload;
import com.rental.listingbooking.outbox.RoommateApplicationCreatedPayload;
import com.rental.listingbooking.outbox.RoommateApplicationRejectedPayload;
import com.rental.listingbooking.repository.RoommateApplicationRepository;
import com.rental.listingbooking.repository.RoommatePostRepository;
import com.rental.listingbooking.service.IdentityFileStorage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoommateService {

    private final RoommatePostRepository postRepository;
    private final RoommateApplicationRepository applicationRepository;
    private final ContractClient contractClient;
    private final UserClient userClient;
    private final ListingEventPublisher publisher;
    private final IdentityFileStorage identityFileStorage;

    @Transactional
    public RoommatePostResponse createPost(RoommatePostCreateDto dto, Long customerId) {
        // Feign call #3: Xác thực người dùng là khách thuê đang ở phòng này
        ContractClient.RoomTenantCheckResponse check;
        try {
            check = contractClient.checkRoomTenant(dto.roomId(), customerId);
        } catch (Exception ex) {
            log.error("Không thể kết nối contract-tenant-service để kiểm tra người thuê: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "Dịch vụ hợp đồng tạm thời không khả dụng, vui lòng thử lại sau");
        }

        if (check == null || !check.isTenant()) {
            throw new BusinessException("NOT_A_TENANT_OF_ROOM",
                    "Chỉ khách thuê đang có hợp đồng hiệu lực tại phòng này mới được đăng tin tìm bạn ở ghép", HttpStatus.FORBIDDEN);
        }

        RoommatePost post = new RoommatePost();
        post.setCustomerId(customerId);
        post.setRoomId(dto.roomId());
        post.setBuildingId(check.buildingId() != null ? check.buildingId() : 0L);
        post.setTitle(dto.title().trim());
        post.setDescription(dto.description() != null ? dto.description().trim() : null);
        post.setBudgetShare(dto.budgetShare() != null ? dto.budgetShare() : BigDecimal.ZERO);
        post.setStatus("OPEN");
        post.setCreatedAt(Instant.now());

        return toPostResponse(postRepository.save(post));
    }

    @Transactional(readOnly = true)
    public PageResponse<RoommatePostResponse> searchPosts(Long buildingId, BigDecimal maxBudget, String keyword, Pageable pageable) {
        Page<RoommatePost> page = postRepository.searchOpen(buildingId, maxBudget, keyword, pageable);
        return PageResponse.from(page.map(this::toPostResponse));
    }

    @Transactional(readOnly = true)
    public RoommatePostResponse getPostById(Long id) {
        RoommatePost post = postRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tin tìm bạn ở ghép ID " + id));
        return toPostResponse(post);
    }

    @Transactional(readOnly = true)
    public List<RoommatePostResponse> getMyPosts(Long customerId) {
        return postRepository.findByCustomerId(customerId).stream().map(this::toPostResponse).toList();
    }

    @Transactional
    public void closePost(Long id, Long customerId) {
        RoommatePost post = postRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tin tìm bạn ở ghép ID " + id));

        if (!post.getCustomerId().equals(customerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền đóng tin đăng này");
        }

        post.setStatus("CLOSED");
        postRepository.save(post);
    }

    @Transactional
    public RoommateApplicationResponse applyToPost(Long postId, RoommateApplicationCreateDto dto, Long applicantCustomerId) {
        RoommatePost post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tin tìm bạn ở ghép ID " + postId));

        if (!"OPEN".equals(post.getStatus())) {
            throw new BusinessException("POST_CLOSED", "Tin đăng này đã đóng", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        if (post.getCustomerId().equals(applicantCustomerId)) {
            throw new BusinessException("SELF_APPLICATION", "Bạn không thể tự ứng tuyển vào bài đăng của chính mình", HttpStatus.BAD_REQUEST);
        }

        // Bảng có ràng buộc duy nhất (roommate_post_id, customer_id) nên mỗi khách chỉ ứng tuyển một lần.
        applicationRepository.findByRoommatePostIdAndCustomerId(postId, applicantCustomerId)
                .ifPresent(existing -> {
                    throw new BusinessException("ALREADY_APPLIED", "Bạn đã ứng tuyển vào tin này", HttpStatus.CONFLICT);
                });

        RoommateApplication app = new RoommateApplication();
        app.setRoommatePostId(postId);
        app.setCustomerId(applicantCustomerId);
        app.setFullName(dto.fullName().trim());
        app.setPhone(dto.phone().trim());
        app.setCccdNumber(dto.cccdNumber().trim());
        app.setDateOfBirth(dto.dateOfBirth());
        app.setGender(dto.gender());
        app.setCccdIssueDate(dto.cccdIssueDate());
        app.setCccdIssuePlace(dto.cccdIssuePlace() == null ? null : dto.cccdIssuePlace().trim());
        app.setPermanentAddress(dto.permanentAddress() == null ? null : dto.permanentAddress().trim());
        app.setOccupation(dto.occupation() == null ? null : dto.occupation().trim());
        app.setMoveInDate(dto.moveInDate());
        app.setCccdFrontKey(verifyIdentityKey(dto.cccdFrontKey(), applicantCustomerId, "front"));
        app.setCccdBackKey(verifyIdentityKey(dto.cccdBackKey(), applicantCustomerId, "back"));
        app.setMessage(dto.message().trim());
        app.setStatus("PENDING");
        app.setCreatedAt(Instant.now());

        RoommateApplication saved = applicationRepository.save(app);

        publisher.publishRoommateApplicationCreated(new RoommateApplicationCreatedPayload(
                saved.getId(),
                post.getId(),
                post.getCustomerId(),
                saved.getCustomerId()
        ));

        return toApplicationResponse(saved, null);
    }

    @Transactional(readOnly = true)
    public List<RoommateApplicationResponse> getMyApplications(Long customerId) {
        return applicationRepository.findByCustomerId(customerId).stream()
                .map(app -> toApplicationResponse(app, null)).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<RoommateApplicationResponse> getPostApplications(Long postId, Long ownerCustomerId, String status, Pageable pageable) {
        RoommatePost post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy tin tìm bạn ở ghép ID " + postId));
        if (!post.getCustomerId().equals(ownerCustomerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem đơn ứng tuyển của tin này");
        }

        Page<RoommateApplication> page = (status != null && !status.isBlank())
                ? applicationRepository.findByRoommatePostIdAndStatus(postId, status.trim().toUpperCase(), pageable)
                : applicationRepository.findByRoommatePostId(postId, pageable);

        // Chủ bài là CUSTOMER nên không đọc được /api/v1/users/{id}; lấy tên qua internal API.
        Map<Long, String> names = customerNames(page.getContent());
        return PageResponse.from(page.map(app -> toApplicationResponse(app, names.get(app.getCustomerId()))));
    }

    @Transactional
    public RoommateApplicationResponse acceptApplication(Long applicationId, Long ownerCustomerId) {
        RoommateApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn ứng tuyển ID " + applicationId));

        RoommatePost post = postRepository.findById(app.getRoommatePostId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bài đăng tương ứng"));

        if (!post.getCustomerId().equals(ownerCustomerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền duyệt đơn ứng tuyển này");
        }

        if (!"PENDING".equals(app.getStatus())) {
            throw new BusinessException("INVALID_STATUS", "Đơn ứng tuyển đã được xử lý", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        app.setStatus("ACCEPTED_PENDING");
        RoommateApplication saved = applicationRepository.save(app);

        // Bắn event RoommateApplicationAccepted để contract-tenant-service tiếp nhận và xử lý thêm người vào hợp đồng
        publisher.publishRoommateApplicationAccepted(new RoommateApplicationAcceptedPayload(
                saved.getId(),
                post.getId(),
                post.getRoomId(),
                post.getBuildingId(),
                post.getCustomerId(),
                saved.getCustomerId(),
                saved.getFullName(),
                saved.getPhone(),
                saved.getCccdNumber(),
                saved.getMoveInDate()
        ));

        return toApplicationResponse(saved, null);
    }

    @Transactional
    public RoommateApplicationResponse markApplicationRead(Long applicationId, Long ownerCustomerId) {
        RoommateApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn ứng tuyển ID " + applicationId));
        RoommatePost post = postRepository.findById(app.getRoommatePostId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bài đăng tương ứng"));

        if (!post.getCustomerId().equals(ownerCustomerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem đơn ứng tuyển của tin này");
        }

        if (app.getReadAt() == null) {
            app.setReadAt(Instant.now());
            applicationRepository.save(app);
        }
        return toApplicationResponse(app, null);
    }

    @Transactional
    public RoommateApplicationResponse rejectApplication(Long applicationId, Long ownerCustomerId) {
        RoommateApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn ứng tuyển ID " + applicationId));

        RoommatePost post = postRepository.findById(app.getRoommatePostId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bài đăng tương ứng"));

        if (!post.getCustomerId().equals(ownerCustomerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền từ chối đơn ứng tuyển này");
        }

        if (!"PENDING".equals(app.getStatus())) {
            throw new BusinessException("INVALID_STATUS", "Đơn ứng tuyển đã được xử lý", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        app.setStatus("REJECTED");
        RoommateApplication saved = applicationRepository.save(app);

        publisher.publishRoommateApplicationRejected(new RoommateApplicationRejectedPayload(
                saved.getId(),
                post.getId(),
                saved.getCustomerId()
        ));

        return toApplicationResponse(saved, null);
    }
    @Transactional(readOnly = true)
    public IdentityFileStorage.Download openApplicationIdentityFile(Long applicationId, String side, Long ownerCustomerId) {
        RoommateApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn ứng tuyển ID " + applicationId));
        RoommatePost post = postRepository.findById(app.getRoommatePostId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bài đăng tương ứng"));
        if (!post.getCustomerId().equals(ownerCustomerId) && !app.getCustomerId().equals(ownerCustomerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem ảnh CCCD của đơn này");
        }
        String normalizedSide = side == null ? "" : side.trim().toUpperCase(Locale.ROOT);
        if (!"FRONT".equals(normalizedSide) && !"BACK".equals(normalizedSide)) {
            throw new BusinessException("INVALID_IDENTITY_SIDE", "Mặt CCCD phải là FRONT hoặc BACK", HttpStatus.BAD_REQUEST);
        }
        String objectKey = "FRONT".equals(normalizedSide) ? app.getCccdFrontKey() : app.getCccdBackKey();
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Hồ sơ chưa có ảnh CCCD này");
        }
        return identityFileStorage.open(objectKey);
    }

    private String verifyIdentityKey(String key, Long customerId, String side) {
        String expectedPrefix = "identity/%d/%s-".formatted(customerId, side);
        if (key == null || !key.startsWith(expectedPrefix) || key.contains("..") || key.contains("\\")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ảnh CCCD tải lên không hợp lệ hoặc không thuộc tài khoản của bạn");
        }
        return key;
    }

    private RoommatePostResponse toPostResponse(RoommatePost p) {
        return new RoommatePostResponse(
                p.getId(),
                p.getCustomerId(),
                p.getRoomId(),
                p.getBuildingId(),
                p.getTitle(),
                p.getDescription(),
                p.getBudgetShare(),
                p.getStatus(),
                p.getCreatedAt()
        );
    }

    private RoommateApplicationResponse toApplicationResponse(RoommateApplication a, String customerName) {
        return new RoommateApplicationResponse(
                a.getId(), a.getRoommatePostId(), a.getCustomerId(), customerName,
                a.getFullName(), a.getPhone(), a.getCccdNumber(), a.getMessage(),
                a.getDateOfBirth(), a.getGender(), a.getCccdIssueDate(), a.getCccdIssuePlace(),
                a.getPermanentAddress(), a.getOccupation(), a.getMoveInDate(),
                a.getCccdFrontKey() != null && !a.getCccdFrontKey().isBlank(),
                a.getCccdBackKey() != null && !a.getCccdBackKey().isBlank(),
                a.getStatus(), a.getCreatedAt(), a.getReadAt());
    }
    private Map<Long, String> customerNames(Collection<RoommateApplication> applications) {
        List<Long> ids = applications.stream().map(RoommateApplication::getCustomerId).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        try {
            return userClient.getCustomerSummaries(ids).stream()
                    .filter(summary -> summary.fullName() != null && !summary.fullName().isBlank())
                    .collect(Collectors.toMap(UserClient.CustomerSummary::id, UserClient.CustomerSummary::fullName));
        } catch (Exception ex) {
            // Không làm hỏng danh sách đơn nếu user-notification tạm thời không phản hồi.
            log.warn("Không lấy được tên khách hàng cho đơn ở ghép: {}", ex.getMessage());
            return Map.of();
        }
    }
}
