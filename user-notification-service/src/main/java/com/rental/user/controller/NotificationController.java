package com.rental.user.controller;

import com.rental.user.dto.DeviceRequest;
import com.rental.user.dto.NotificationResponse;
import com.rental.user.dto.PageResponse;
import com.rental.user.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController @Validated @RequestMapping("/api/v1")
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping("/notifications") @PreAuthorize("isAuthenticated()")
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(unreadOnly, page, size);
    }

    @GetMapping("/notifications/unread-count") @PreAuthorize("isAuthenticated()")
    public CountResponse unreadCount() { return new CountResponse(service.unreadCount()); }

    @PutMapping("/notifications/{id}/read") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markRead(@PathVariable Long id) { service.markRead(id); return ResponseEntity.noContent().build(); }

    @PutMapping("/notifications/read-all") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAllRead() { service.markAllRead(); return ResponseEntity.noContent().build(); }

    @PostMapping("/devices") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> registerDevice(@Valid @RequestBody DeviceRequest request) {
        service.registerDevice(request); return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/devices/{token}") @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> removeDevice(@PathVariable String token) {
        service.removeDevice(token); return ResponseEntity.noContent().build();
    }

    public record CountResponse(long count) { }
}
