package com.rental.user.controller;

import com.rental.user.dto.DeviceRequest;
import com.rental.user.dto.NotificationResponse;
import com.rental.common.dto.PageResponse;
import com.rental.user.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    @Tag(name = "Thông báo")
    @Operation(summary = "Danh sách thông báo của tôi", description = "Trả về thông báo phân trang của người dùng hiện tại, hỗ trợ lọc chỉ tin chưa đọc (unreadOnly=true).")
    public PageResponse<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(unreadOnly, page, size);
    }

    @GetMapping("/notifications/unread-count") @PreAuthorize("isAuthenticated()")
    @Tag(name = "Thông báo")
    @Operation(summary = "Đếm thông báo chưa đọc", description = "Trả về tổng số thông báo chưa đọc của người dùng hiện tại.")
    public CountResponse unreadCount() { return new CountResponse(service.unreadCount()); }

    @PutMapping("/notifications/{id}/read") @PreAuthorize("isAuthenticated()")
    @Tag(name = "Thông báo")
    @Operation(summary = "Đánh dấu đã đọc một thông báo", description = "Đánh dấu một thông báo thuộc sở hữu của người dùng là đã đọc.")
    public ResponseEntity<Void> markRead(@PathVariable Long id) { service.markRead(id); return ResponseEntity.noContent().build(); }

    @PutMapping("/notifications/read-all") @PreAuthorize("isAuthenticated()")
    @Tag(name = "Thông báo")
    @Operation(summary = "Đánh dấu đã đọc tất cả thông báo", description = "Đánh dấu toàn bộ thông báo của người dùng hiện tại là đã đọc.")
    public ResponseEntity<Void> markAllRead() { service.markAllRead(); return ResponseEntity.noContent().build(); }

    @PostMapping("/devices") @PreAuthorize("isAuthenticated()")
    @Tag(name = "Thiết bị Push")
    @Operation(summary = "Đăng ký token thiết bị push", description = "Lưu FCM/device token để nhận thông báo đẩy theo người dùng hiện tại.")
    public ResponseEntity<Void> registerDevice(@Valid @RequestBody DeviceRequest request) {
        service.registerDevice(request); return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/devices/{token}") @PreAuthorize("isAuthenticated()")
    @Tag(name = "Thiết bị Push")
    @Operation(summary = "Hủy đăng ký token thiết bị", description = "Xóa token thiết bị khỏi danh sách nhận thông báo đẩy khi đăng xuất.")
    public ResponseEntity<Void> removeDevice(@PathVariable String token) {
        service.removeDevice(token); return ResponseEntity.noContent().build();
    }

    public record CountResponse(long count) { }
}
