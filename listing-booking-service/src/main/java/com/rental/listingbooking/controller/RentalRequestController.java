package com.rental.listingbooking.controller;

import com.rental.common.security.HeaderUser;
import com.rental.common.dto.PageResponse;
import com.rental.listingbooking.dto.RejectRentalRequestDto;
import com.rental.listingbooking.dto.RentalRequestCreateDto;
import com.rental.listingbooking.dto.RentalRequestResponse;
import com.rental.listingbooking.service.IdentityFileStorage;
import com.rental.listingbooking.service.RentalRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/rental-requests")
@Tag(name = "Yêu cầu thuê phòng", description = "Khách gửi/hủy yêu cầu thuê, upload ảnh CCCD và nhân sự xác minh/duyệt/từ chối")
@RequiredArgsConstructor
public class RentalRequestController {

    private final RentalRequestService requestService;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Gửi yêu cầu thuê phòng", description = "Khách hàng nộp yêu cầu thuê phòng kèm thông tin CCCD, ngày dự kiến vào ở và hình thức thanh toán (CASH hoặc BANK_TRANSFER).")
    public RentalRequestResponse createRequest(
            @Valid @RequestBody RentalRequestCreateDto dto,
            @AuthenticationPrincipal HeaderUser user) {
        Long customerId = Long.parseLong(user.getUserId());
        return requestService.createRequest(dto, customerId);
    }

    @GetMapping({"/my", "/mine"})
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Danh sách yêu cầu thuê của tôi", description = "Khách hàng xem lịch sử các yêu cầu thuê đã gửi, lọc tùy chọn theo status.")
    public PageResponse<RentalRequestResponse> getMyRequests(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal HeaderUser user,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Long customerId = Long.parseLong(user.getUserId());
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 50), pageable.getSort());
        return requestService.getMyRequests(customerId, status, capped);
    }

    @GetMapping({"", "/manage"})
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")
    @Operation(summary = "Quản lý danh sách yêu cầu thuê", description = "Nhân sự/Admin lọc yêu cầu thuê theo buildingId, listingId, status và unread.")
    public PageResponse<RentalRequestResponse> getManageRequests(
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long listingId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "false") boolean unread,
            @ParameterObject @PageableDefault(page = 0, size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        return requestService.getManageRequests(buildingId, listingId, status, unread, capped);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'QUAN_LY', 'SALE', 'KY_THUAT', 'ADMIN')")
    @Operation(summary = "Chi tiết yêu cầu thuê phòng", description = "Xem thông tin chi tiết một yêu cầu thuê phòng theo ID.")
    public RentalRequestResponse getRequestById(
            @PathVariable Long id,
            @AuthenticationPrincipal HeaderUser user) {
        return requestService.getRequestById(id, Long.parseLong(user.getUserId()), user.getUserType());
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Khách hủy yêu cầu thuê", description = "Khách hàng hủy yêu cầu thuê đang ở trạng thái chờ xử lý.")
    public ResponseEntity<Void> cancelRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal HeaderUser user) {
        requestService.cancelRequest(id, Long.parseLong(user.getUserId()));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")
    @Operation(summary = "Xác minh hồ sơ yêu cầu thuê", description = "Sale/Quản lý xác nhận đã kiểm tra thông tin CCCD và nhu cầu của khách.")
    public RentalRequestResponse verifyRequest(
            @PathVariable Long id,
            @AuthenticationPrincipal HeaderUser user) {
        return requestService.verifyRequest(id, Long.parseLong(user.getUserId()));
    }

    @PutMapping("/{id}/accept")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'ADMIN')")
    @Operation(summary = "Chấp nhận yêu cầu thuê & sinh hợp đồng nháp", description = "Quản lý/Admin duyệt yêu cầu thuê và kích hoạt tạo hợp đồng nháp tương ứng.")
    public RentalRequestResponse acceptRequest(@PathVariable Long id) {
        return requestService.acceptRequest(id);
    }

    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")
    @Operation(summary = "Từ chối yêu cầu thuê", description = "Từ chối yêu cầu thuê phòng kèm lý do cụ thể.")
    public RentalRequestResponse rejectRequest(
            @PathVariable Long id,
            @Valid @RequestBody RejectRentalRequestDto dto) {
        return requestService.rejectRequest(id, dto);
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")
    @Operation(summary = "Đánh dấu đã xem yêu cầu thuê", description = "Đánh dấu nhân sự đã đọc yêu cầu thuê mới gửi.")
    public RentalRequestResponse markRead(@PathVariable Long id) {
        return requestService.markRead(id);
    }

    @PostMapping("/identity-files")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tải ảnh CCCD (mặt trước/sau)", description = "Upload ảnh CCCD (front/back) lên MinIO bucket riêng tư trước khi gửi yêu cầu thuê hoặc đơn ở ghép.")
    public RentalRequestService.IdentityUpload uploadIdentityFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam("side") String side,
            @AuthenticationPrincipal HeaderUser user) {
        return requestService.uploadIdentityFile(file, side, Long.parseLong(user.getUserId()));
    }

    @GetMapping("/{id}/identity-files/{side}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'QUAN_LY', 'SALE', 'ADMIN')")
    @Operation(summary = "Xem/tải ảnh CCCD của yêu cầu thuê", description = "Stream ảnh CCCD (front/back) qua endpoint có kiểm tra quyền truy cập.")
    public ResponseEntity<InputStreamResource> downloadIdentityFile(
            @PathVariable Long id,
            @PathVariable String side,
            @AuthenticationPrincipal HeaderUser user) {
        IdentityFileStorage.Download download = requestService.openIdentityFile(
                id, side, Long.parseLong(user.getUserId()), user.getUserType());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(download.filename()).build().toString())
                .body(new InputStreamResource(download.stream()));
    }
}
