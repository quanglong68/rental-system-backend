package com.rental.user.service;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.user.dto.DeviceRequest;
import com.rental.user.dto.NotificationResponse;
import com.rental.user.dto.PageResponse;
import com.rental.user.entity.DeviceToken;
import com.rental.user.entity.Notification;
import com.rental.user.repository.DeviceTokenRepository;
import com.rental.user.repository.NotificationRepository;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class NotificationService {
    private final NotificationRepository notifications;
    private final DeviceTokenRepository devices;

    public NotificationService(NotificationRepository notifications, DeviceTokenRepository devices) {
        this.notifications = notifications; this.devices = devices;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(boolean unreadOnly, int page, int size) {
        validatePage(page, size);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        var result = unreadOnly ? notifications.findByRecipientIdAndReadFalse(CurrentUser.id(), pageable)
                : notifications.findByRecipientId(CurrentUser.id(), pageable);
        return PageResponse.from(result.map(NotificationService::response));
    }

    @Transactional(readOnly = true)
    public long unreadCount() { return notifications.countByRecipientIdAndReadFalse(CurrentUser.id()); }

    public void markRead(Long id) {
        Notification notification = notifications.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Khong tim thay thong bao"));
        if (!notification.getRecipientId().equals(CurrentUser.id()))
            throw new BusinessException(ErrorCode.NOT_FOUND, "Khong tim thay thong bao");
        notification.setRead(true);
    }

    public void markAllRead() { notifications.markAllReadByRecipientId(CurrentUser.id()); }

    public void registerDevice(DeviceRequest request) {
        Long accountId = CurrentUser.id();
        Optional<DeviceToken> existing = devices.findByToken(request.token());
        DeviceToken device = existing.orElseGet(DeviceToken::new);
        device.setAccountId(accountId); device.setToken(request.token()); device.setPlatform(request.platform());
        device.touch(); devices.save(device);
    }

    public void removeDevice(String token) { devices.deleteByAccountIdAndToken(CurrentUser.id(), token); }

    private static NotificationResponse response(Notification n) {
        return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getContent(), n.getRefType(),
                n.getRefId(), n.isRead(), n.getCreatedAt());
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "page phai >= 0 va size trong khoang 1..100");
    }
}
