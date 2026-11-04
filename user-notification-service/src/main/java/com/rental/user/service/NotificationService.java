package com.rental.user.service;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.user.dto.DeviceRequest;
import com.rental.user.dto.NotificationResponse;
import com.rental.common.dto.PageResponse;
import com.rental.user.entity.DeviceToken;
import com.rental.user.entity.Notification;
import com.rental.user.repository.DeviceTokenRepository;
import com.rental.user.repository.NotificationRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional
public class NotificationService {
    /**
     * Các thông báo sinh trước khi NotificationEventProcessor format ngày vẫn lưu ISO trong DB.
     * Chuẩn hóa lúc đọc để dữ liệu cũ hiển thị giống dữ liệu mới mà không cần sửa bản ghi.
     */
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern ISO_DATE_TIME = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2})T(\\d{2}:\\d{2})(?::\\d{2})?(?:\\.\\d+)?(Z|[+-]\\d{2}:?\\d{2})?");
    private static final Pattern ISO_DATE = Pattern.compile("(?<!\\d)(\\d{4}-\\d{2}-\\d{2})(?!\\d)");
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
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy thông báo"));
        if (!notification.getRecipientId().equals(CurrentUser.id()))
            throw new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy thông báo");
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
        return new NotificationResponse(n.getId(), n.getType(), formatLegacyDates(n.getTitle()),
                formatLegacyDates(n.getContent()), n.getRefType(), n.getRefId(), n.isRead(), n.getCreatedAt());
    }

    static String formatLegacyDates(String value) {
        if (value == null || value.isEmpty()) return value;
        Matcher dateTime = ISO_DATE_TIME.matcher(value);
        StringBuilder withDateTimes = new StringBuilder();
        while (dateTime.find()) {
            String token = dateTime.group();
            String formatted;
            try {
                if (dateTime.group(3) != null) {
                    formatted = OffsetDateTime.parse(token).atZoneSameInstant(VN).format(DATE_TIME);
                } else {
                    formatted = LocalDateTime.parse(dateTime.group(1) + "T" + dateTime.group(2)).format(DATE_TIME);
                }
            } catch (RuntimeException ex) {
                formatted = token;
            }
            dateTime.appendReplacement(withDateTimes, Matcher.quoteReplacement(formatted));
        }
        dateTime.appendTail(withDateTimes);

        Matcher date = ISO_DATE.matcher(withDateTimes.toString());
        StringBuilder withDates = new StringBuilder();
        while (date.find()) {
            String formatted;
            try { formatted = LocalDate.parse(date.group(1)).format(DATE); }
            catch (RuntimeException ex) { formatted = date.group(); }
            date.appendReplacement(withDates, Matcher.quoteReplacement(formatted));
        }
        date.appendTail(withDates);
        return withDates.toString();
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "page phải >= 0 và size trong khoảng 1..100");
    }
}
