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
        // Feign call #3: XÃ¡c thá»±c ngÆ°á»i dÃ¹ng lÃ  khÃ¡ch thuÃª Ä‘ang á»Ÿ phÃ²ng nÃ y
        ContractClient.RoomTenantCheckResponse check;
        try {
            check = contractClient.checkRoomTenant(dto.roomId(), customerId);
        } catch (Exception ex) {
            log.error("KhÃ´ng thá»ƒ káº¿t ná»‘i contract-tenant-service Ä‘á»ƒ kiá»ƒm tra ngÆ°á»i thuÃª: {}", ex.getMessage());
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "Dá»‹ch vá»¥ há»£p Ä‘á»“ng táº¡m thá»i khÃ´ng kháº£ dá»¥ng, vui lÃ²ng thá»­ láº¡i sau");
        }

        if (check == null || !check.isTenant()) {
            throw new BusinessException("NOT_A_TENANT_OF_ROOM",
                    "Chá»‰ khÃ¡ch thuÃª Ä‘ang cÃ³ há»£p Ä‘á»“ng hiá»‡u lá»±c táº¡i phÃ²ng nÃ y má»›i Ä‘Æ°á»£c Ä‘Äƒng tin tÃ¬m báº¡n á»Ÿ ghÃ©p", HttpStatus.FORBIDDEN);
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
                .orElseThrow(() -> new NotFoundException("KhÃ´ng tÃ¬m tháº¥y tin tÃ¬m báº¡n á»Ÿ ghÃ©p ID " + id));
        return toPostResponse(post);
    }

    @Transactional(readOnly = true)
    public List<RoommatePostResponse> getMyPosts(Long customerId) {
        return postRepository.findByCustomerId(customerId).stream().map(this::toPostResponse).toList();
    }

    @Transactional
    public void closePost(Long id, Long customerId) {
        RoommatePost post = postRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("KhÃ´ng tÃ¬m tháº¥y tin tÃ¬m báº¡n á»Ÿ ghÃ©p ID " + id));

        if (!post.getCustomerId().equals(customerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Báº¡n khÃ´ng cÃ³ quyá»n Ä‘Ã³ng tin Ä‘Äƒng nÃ y");
        }

        post.setStatus("CLOSED");
        postRepository.save(post);
    }

    @Transactional
    public RoommateApplicationResponse applyToPost(Long postId, RoommateApplicationCreateDto dto, Long applicantCustomerId) {
        RoommatePost post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("KhÃ´ng tÃ¬m tháº¥y tin tÃ¬m báº¡n á»Ÿ ghÃ©p ID " + postId));

        if (!"OPEN".equals(post.getStatus())) {
            throw new BusinessException("POST_CLOSED", "Tin Ä‘Äƒng nÃ y Ä‘Ã£ Ä‘Ã³ng", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        if (post.getCustomerId().equals(applicantCustomerId)) {
            throw new BusinessException("SELF_APPLICATION", "Báº¡n khÃ´ng thá»ƒ tá»± á»©ng tuyá»ƒn vÃ o bÃ i Ä‘Äƒng cá»§a chÃ­nh mÃ¬nh", HttpStatus.BAD_REQUEST);
        }

        // Báº£ng cÃ³ rÃ ng buá»™c duy nháº¥t (roommate_post_id, customer_id) nÃªn má»—i khÃ¡ch chá»‰ á»©ng tuyá»ƒn má»™t láº§n.
        applicationRepository.findByRoommatePostIdAndCustomerId(postId, applicantCustomerId)
                .ifPresent(existing -> {
                    throw new BusinessException("ALREADY_APPLIED", "Báº¡n Ä‘Ã£ á»©ng tuyá»ƒn vÃ o tin nÃ y", HttpStatus.CONFLICT);
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
                .orElseThrow(() -> new NotFoundException("KhÃ´ng tÃ¬m tháº¥y tin tÃ¬m báº¡n á»Ÿ ghÃ©p ID " + postId));
        if (!post.getCustomerId().equals(ownerCustomerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Báº¡n khÃ´ng cÃ³ quyá»n xem Ä‘Æ¡n á»©ng tuyá»ƒn cá»§a tin nÃ y");
        }

        Page<RoommateApplication> page = (status != null && !status.isBlank())
                ? applicationRepository.findByRoommatePostIdAndStatus(postId, status.trim().toUpperCase(), pageable)
                : applicationRepository.findByRoommatePostId(postId, pageable);

        // Chá»§ bÃ i lÃ  CUSTOMER nÃªn khÃ´ng Ä‘á»c Ä‘Æ°á»£c /api/v1/users/{id}; láº¥y tÃªn qua internal API.
        Map<Long, String> names = customerNames(page.getContent());
        return PageResponse.from(page.map(app -> toApplicationResponse(app, names.get(app.getCustomerId()))));
    }

    @Transactional
    public RoommateApplicationResponse acceptApplication(Long applicationId, Long ownerCustomerId) {
        RoommateApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("KhÃ´ng tÃ¬m tháº¥y Ä‘Æ¡n á»©ng tuyá»ƒn ID " + applicationId));

        RoommatePost post = postRepository.findById(app.getRoommatePostId())
                .orElseThrow(() -> new NotFoundException("KhÃ´ng tÃ¬m tháº¥y bÃ i Ä‘Äƒng tÆ°Æ¡ng á»©ng"));

        if (!post.getCustomerId().equals(ownerCustomerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Báº¡n khÃ´ng cÃ³ quyá»n duyá»‡t Ä‘Æ¡n á»©ng tuyá»ƒn nÃ y");
        }

        if (!"PENDING".equals(app.getStatus())) {
            throw new BusinessException("INVALID_STATUS", "ÄÆ¡n á»©ng tuyá»ƒn Ä‘Ã£ Ä‘Æ°á»£c xá»­ lÃ½", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        app.setStatus("ACCEPTED_PENDING");
        RoommateApplication saved = applicationRepository.save(app);

        // Báº¯n event RoommateApplicationAccepted Ä‘á»ƒ contract-tenant-service tiáº¿p nháº­n vÃ  xá»­ lÃ½ thÃªm ngÆ°á»i vÃ o há»£p Ä‘á»“ng
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
                .orElseThrow(() -> new NotFoundException("KhÃ´ng tÃ¬m tháº¥y Ä‘Æ¡n á»©ng tuyá»ƒn ID " + applicationId));
        RoommatePost post = postRepository.findById(app.getRoommatePostId())
                .orElseThrow(() -> new NotFoundException("KhÃ´ng tÃ¬m tháº¥y bÃ i Ä‘Äƒng tÆ°Æ¡ng á»©ng"));

        if (!post.getCustomerId().equals(ownerCustomerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Báº¡n khÃ´ng cÃ³ quyá»n xem Ä‘Æ¡n á»©ng tuyá»ƒn cá»§a tin nÃ y");
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
                .orElseThrow(() -> new NotFoundException("KhÃ´ng tÃ¬m tháº¥y Ä‘Æ¡n á»©ng tuyá»ƒn ID " + applicationId));

        RoommatePost post = postRepository.findById(app.getRoommatePostId())
                .orElseThrow(() -> new NotFoundException("KhÃ´ng tÃ¬m tháº¥y bÃ i Ä‘Äƒng tÆ°Æ¡ng á»©ng"));

        if (!post.getCustomerId().equals(ownerCustomerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Báº¡n khÃ´ng cÃ³ quyá»n tá»« chá»‘i Ä‘Æ¡n á»©ng tuyá»ƒn nÃ y");
        }

        if (!"PENDING".equals(app.getStatus())) {
            throw new BusinessException("INVALID_STATUS", "ÄÆ¡n á»©ng tuyá»ƒn Ä‘Ã£ Ä‘Æ°á»£c xá»­ lÃ½", HttpStatus.UNPROCESSABLE_ENTITY);
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
            // KhÃ´ng lÃ m há»ng danh sÃ¡ch Ä‘Æ¡n náº¿u user-notification táº¡m thá»i khÃ´ng pháº£n há»“i.
            log.warn("KhÃ´ng láº¥y Ä‘Æ°á»£c tÃªn khÃ¡ch hÃ ng cho Ä‘Æ¡n á»Ÿ ghÃ©p: {}", ex.getMessage());
            return Map.of();
        }
    }
}
