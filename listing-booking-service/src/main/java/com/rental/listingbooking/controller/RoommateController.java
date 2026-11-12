package com.rental.listingbooking.controller;

import com.rental.common.security.HeaderUser;
import com.rental.common.dto.PageResponse;
import com.rental.listingbooking.dto.RoommateApplicationCreateDto;
import com.rental.listingbooking.dto.RoommateApplicationResponse;
import com.rental.listingbooking.dto.RoommatePostCreateDto;
import com.rental.listingbooking.dto.RoommatePostResponse;
import com.rental.listingbooking.service.RoommateService;
import com.rental.listingbooking.service.IdentityFileStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RoommateController {

    private final RoommateService roommateService;

    @PostMapping("/api/v1/roommate-posts")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Tag(name = "Tìm bạn ở ghép")
    @Operation(summary = "Đăng tin tìm bạn ở ghép", description = "Người thuê đang có hợp đồng tạo tin tìm thêm bạn ở ghép cùng phòng.")
    public RoommatePostResponse createPost(
            @Valid @RequestBody RoommatePostCreateDto dto,
            @AuthenticationPrincipal HeaderUser user) {
        Long customerId = Long.parseLong(user.getUserId());
        return roommateService.createPost(dto, customerId);
    }

    @GetMapping("/api/v1/roommate-posts")
    @PreAuthorize("isAuthenticated()")
    @Tag(name = "Tìm bạn ở ghép")
    @Operation(summary = "Tìm kiếm tin ở ghép", description = "Tra cứu phân trang các tin tìm bạn ở ghép theo buildingId, ngân sách tối đa và từ khóa.")
    public PageResponse<RoommatePostResponse> searchPosts(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) BigDecimal maxBudget,
            @RequestParam(required = false) String keyword,
            @ParameterObject @PageableDefault(page = 0, size = 12, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 50), pageable.getSort());
        return roommateService.searchPosts(buildingId, maxBudget, keyword, capped);
    }

    @GetMapping({"/api/v1/roommate-posts/my", "/api/v1/roommate-posts/mine"})
    @PreAuthorize("hasRole('CUSTOMER')")
    @Tag(name = "Tìm bạn ở ghép")
    @Operation(summary = "Tin ở ghép của tôi", description = "Danh sách tin tìm bạn ở ghép do chính khách hàng đăng.")
    public List<RoommatePostResponse> getMyPosts(@AuthenticationPrincipal HeaderUser user) {
        Long customerId = Long.parseLong(user.getUserId());
        return roommateService.getMyPosts(customerId);
    }

    @GetMapping("/api/v1/roommate-posts/{id}")
    @PreAuthorize("isAuthenticated()")
    @Tag(name = "Tìm bạn ở ghép")
    @Operation(summary = "Chi tiết tin ở ghép", description = "Xem chi tiết một tin tìm bạn ở ghép theo ID.")
    public RoommatePostResponse getPostById(@PathVariable Long id) {
        return roommateService.getPostById(id);
    }

    @PutMapping("/api/v1/roommate-posts/{id}/close")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Tag(name = "Tìm bạn ở ghép")
    @Operation(summary = "Đóng tin ở ghép", description = "Chủ bài đăng đóng tin tìm bạn ở ghép khi đã tìm đủ người.")
    public ResponseEntity<Void> closePost(
            @PathVariable Long id,
            @AuthenticationPrincipal HeaderUser user) {
        roommateService.closePost(id, Long.parseLong(user.getUserId()));
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/api/v1/roommate-posts/{id}/apply", "/api/v1/roommate-posts/{id}/applications"})
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Tag(name = "Đơn ứng tuyển ở ghép")
    @Operation(summary = "Gửi đơn ứng tuyển ở ghép", description = "Khách hàng nộp đơn xin ở ghép vào một tin đang mở (kèm thông tin giới thiệu/CCCD).")
    public RoommateApplicationResponse applyToPost(
            @PathVariable Long id,
            @Valid @RequestBody RoommateApplicationCreateDto dto,
            @AuthenticationPrincipal HeaderUser user) {
        Long customerId = Long.parseLong(user.getUserId());
        return roommateService.applyToPost(id, dto, customerId);
    }

    @GetMapping("/api/v1/roommate-posts/{id}/applications")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Tag(name = "Đơn ứng tuyển ở ghép")
    @Operation(summary = "Danh sách đơn ứng tuyển của bài đăng", description = "Chủ tin ở ghép xem các đơn ứng tuyển gửi vào tin của mình.")
    public PageResponse<RoommateApplicationResponse> getPostApplications(
            @PathVariable Long id,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal HeaderUser user,
            @ParameterObject @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long customerId = Long.parseLong(user.getUserId());
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        return roommateService.getPostApplications(id, customerId, status, capped);
    }

    @GetMapping({"/api/v1/roommate-applications/my", "/api/v1/roommate-applications/mine"})
    @PreAuthorize("hasRole('CUSTOMER')")
    @Tag(name = "Đơn ứng tuyển ở ghép")
    @Operation(summary = "Đơn ứng tuyển ở ghép của tôi", description = "Khách hàng xem lại các đơn ứng tuyển ở ghép mình đã nộp.")
    public List<RoommateApplicationResponse> getMyApplications(@AuthenticationPrincipal HeaderUser user) {
        Long customerId = Long.parseLong(user.getUserId());
        return roommateService.getMyApplications(customerId);
    }

    @PutMapping("/api/v1/roommate-applications/{id}/accept")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Tag(name = "Đơn ứng tuyển ở ghép")
    @Operation(summary = "Chấp nhận đơn ứng tuyển ở ghép", description = "Chủ tin chấp nhận ứng viên ở ghép và kích hoạt thêm người vào hợp đồng/phòng.")
    public RoommateApplicationResponse acceptApplication(
            @PathVariable Long id,
            @AuthenticationPrincipal HeaderUser user) {
        Long customerId = Long.parseLong(user.getUserId());
        return roommateService.acceptApplication(id, customerId);
    }

    @PutMapping("/api/v1/roommate-applications/{id}/reject")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Tag(name = "Đơn ứng tuyển ở ghép")
    @Operation(summary = "Từ chối đơn ứng tuyển ở ghép", description = "Chủ tin từ chối một đơn ứng tuyển ở ghép.")
    public RoommateApplicationResponse rejectApplication(
            @PathVariable Long id,
            @AuthenticationPrincipal HeaderUser user) {
        Long customerId = Long.parseLong(user.getUserId());
        return roommateService.rejectApplication(id, customerId);
    }

    @GetMapping("/api/v1/roommate-applications/{id}/identity-files/{side}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Tag(name = "Đơn ứng tuyển ở ghép")
    @Operation(summary = "Xem ảnh CCCD ứng viên ở ghép", description = "Chủ tin hoặc chính ứng viên tải ảnh CCCD (front/back) đính kèm đơn ứng tuyển.")
    public ResponseEntity<InputStreamResource> downloadApplicationIdentityFile(
            @PathVariable Long id,
            @PathVariable String side,
            @AuthenticationPrincipal HeaderUser user) {
        IdentityFileStorage.Download download = roommateService.openApplicationIdentityFile(
                id, side, Long.parseLong(user.getUserId()));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(download.filename()).build().toString())
                .body(new InputStreamResource(download.stream()));
    }

    @PutMapping("/api/v1/roommate-applications/{id}/read")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Tag(name = "Đơn ứng tuyển ở ghép")
    @Operation(summary = "Đánh dấu đã xem đơn ứng tuyển", description = "Chủ tin đánh dấu đã đọc một đơn ứng tuyển ở ghép.")
    public RoommateApplicationResponse markApplicationRead(
            @PathVariable Long id,
            @AuthenticationPrincipal HeaderUser user) {
        Long customerId = Long.parseLong(user.getUserId());
        return roommateService.markApplicationRead(id, customerId);
    }
}
