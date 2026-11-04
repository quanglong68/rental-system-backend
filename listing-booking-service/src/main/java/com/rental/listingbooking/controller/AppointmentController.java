package com.rental.listingbooking.controller;

import com.rental.common.security.HeaderUser;
import com.rental.listingbooking.dto.AppointmentCreateDto;
import com.rental.listingbooking.dto.AppointmentResponse;
import com.rental.common.dto.PageResponse;
import com.rental.listingbooking.dto.UpdateAppointmentStatusDto;
import com.rental.listingbooking.service.AppointmentService;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse createAppointment(@Valid @RequestBody AppointmentCreateDto dto) {
        return appointmentService.createAppointment(dto);
    }

    @PostMapping("/my")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse createMyAppointment(
            @Valid @RequestBody AppointmentCreateDto dto,
            @AuthenticationPrincipal HeaderUser user) {
        Long customerId = Long.parseLong(user.getUserId());
        return appointmentService.createForCustomer(customerId, dto);
    }

    @GetMapping({"/my", "/mine"})
    @PreAuthorize("hasRole('CUSTOMER')")
    public PageResponse<AppointmentResponse> getMyAppointments(
            @AuthenticationPrincipal HeaderUser user,
            @PageableDefault(page = 0, size = 10, sort = "appointmentDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Long customerId = Long.parseLong(user.getUserId());
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 50), pageable.getSort());
        return appointmentService.getMyAppointments(customerId, capped);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'KY_THUAT', 'ADMIN')")
    public PageResponse<AppointmentResponse> searchAppointments(
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "false") boolean unread,
            @RequestParam(required = false) Instant fromTime,
            @RequestParam(required = false) Instant toTime,
            @PageableDefault(page = 0, size = 20, sort = "appointmentDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Pageable capped = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        return appointmentService.searchAppointments(staffId, buildingId, status, unread, fromTime, toTime, capped);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'QUAN_LY', 'SALE', 'KY_THUAT', 'ADMIN')")
    public AppointmentResponse getAppointmentById(
            @PathVariable Long id,
            @AuthenticationPrincipal HeaderUser user) {
        return appointmentService.getById(id, Long.parseLong(user.getUserId()), user.getUserType());
    }

    @RequestMapping(path = "/{id}/status", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'KY_THUAT', 'ADMIN')")
    public AppointmentResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAppointmentStatusDto dto) {
        return appointmentService.updateStatus(id, dto);
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Void> cancelAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal HeaderUser user) {
        appointmentService.cancelByCustomer(id, Long.parseLong(user.getUserId()));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("hasAnyRole('QUAN_LY', 'SALE', 'KY_THUAT', 'ADMIN')")
    public AppointmentResponse markRead(@PathVariable Long id) {
        return appointmentService.markRead(id);
    }
}
