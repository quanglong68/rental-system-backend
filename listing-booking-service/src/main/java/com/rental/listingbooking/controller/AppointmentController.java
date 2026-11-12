package com.rental.listingbooking.controller;

import com.rental.common.security.HeaderUser;
import com.rental.listingbooking.dto.AppointmentCreateDto;
import com.rental.listingbooking.dto.AppointmentResponse;
import com.rental.common.dto.PageResponse;
import com.rental.listingbooking.dto.UpdateAppointmentStatusDto;
import com.rental.listingbooking.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointments")
@Tag(name = "Lịch hẹn xem phòng", description = "Đặt lịch, tra cứu và cập nhật trạng thái lịch hẹn xem phòng")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo lịch hẹn xem phòng (Nhân sự)", description = "Nhân sự/Sale tạo lịch hẹn xem phòng cho khách hàng và chỉ định nhân viên dẫn xem.")
    public AppointmentResponse createAppointment(@Valid @RequestBody AppointmentCreateDto dto) {
        return appointmentService.createAppointment(dto);
    }

    @PostMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Khách tự đặt lịch xem phòng", description = "Khách hàng chủ động đặt lịch hẹn xem phòng đang mở tin đăng.")
    public AppointmentResponse createMyAppointment(
            @Valid @RequestBody AppointmentCreateDto dto,
            @AuthenticationPrincipal HeaderUser user) {
        Long customerId = Long.parseLong(user.getUserId());
        return appointmentService.createForCustomer(customerId, dto);
    }

    @GetMapping({"/my", "/mine"})
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Danh sách lịch hẹn của tôi", description = "Khách hàng xem lịch hẹn xem phòng đã đặt (hỗ trợ cả /my và /mine).")
    public PageResponse<AppointmentResponse> getMyAppointments(
            @AuthenticationPrincipal HeaderUser user,
            @ParameterObject @PageableDefault(page = 0, size = 10, sort = "appointmentDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Long customerId = Long.parseLong(user.getUserId());
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 50), pageable.getSort());
        return appointmentService.getMyAppointments(customerId, capped);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'KY_THUAT', 'ADMIN')")
    @Operation(summary = "Tìm kiếm lịch hẹn xem phòng", description = "Nhân sự/Admin lọc lịch hẹn theo staffId, buildingId, status, unread và khoảng thời gian.")
    public PageResponse<AppointmentResponse> searchAppointments(
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "false") boolean unread,
            @RequestParam(required = false) Instant fromTime,
            @RequestParam(required = false) Instant toTime,
            @ParameterObject @PageableDefault(page = 0, size = 20, sort = "appointmentDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        return appointmentService.searchAppointments(staffId, buildingId, status, unread, fromTime, toTime, capped);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'QUAN_LY', 'SALE', 'KY_THUAT', 'ADMIN')")
    @Operation(summary = "Chi tiết lịch hẹn", description = "Xem chi tiết một lịch hẹn xem phòng theo ID.")
    public AppointmentResponse getAppointmentById(
            @PathVariable Long id,
            @AuthenticationPrincipal HeaderUser user) {
        return appointmentService.getById(id, Long.parseLong(user.getUserId()), user.getUserType());
    }

    @RequestMapping(path = "/{id}/status", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'KY_THUAT', 'ADMIN')")
    @Operation(summary = "Cập nhật trạng thái lịch hẹn", description = "Xác nhận, hoàn tất hoặc hủy lịch hẹn xem phòng từ phía nhân sự.")
    public AppointmentResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAppointmentStatusDto dto) {
        return appointmentService.updateStatus(id, dto);
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Khách hủy lịch hẹn", description = "Khách hàng hủy lịch hẹn xem phòng do chính mình đặt.")
    public ResponseEntity<Void> cancelAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal HeaderUser user) {
        appointmentService.cancelByCustomer(id, Long.parseLong(user.getUserId()));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'KY_THUAT', 'ADMIN')")
    @Operation(summary = "Đánh dấu đã xem lịch hẹn", description = "Đánh dấu nhân sự đã tiếp nhận/xem thông báo lịch hẹn mới.")
    public AppointmentResponse markRead(@PathVariable Long id) {
        return appointmentService.markRead(id);
    }
}
