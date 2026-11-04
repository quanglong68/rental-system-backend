package com.rental.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationDateFormatTest {

    @Test
    @DisplayName("ISO datetime UTC được đổi sang giờ Việt Nam")
    void convertsUtcDateTime() {
        assertThat(NotificationService.formatLegacyDates("Hạn thanh toán 2026-10-31T03:30:00Z"))
                .isEqualTo("Hạn thanh toán 31/10/2026 10:30");
    }

    @Test
    @DisplayName("ISO datetime có offset giữ nguyên múi giờ đã ghi")
    void convertsOffsetDateTime() {
        assertThat(NotificationService.formatLegacyDates("2026-11-01T08:15:00+07:00"))
                .isEqualTo("01/11/2026 08:15");
    }

    @Test
    @DisplayName("ISO datetime không offset hiển thị theo giờ đã lưu")
    void convertsLocalDateTime() {
        assertThat(NotificationService.formatLegacyDates("2026-11-02T22:05:00.123"))
                .isEqualTo("02/11/2026 22:05");
    }

    @Test
    @DisplayName("Ngày trần không kèm giờ vẫn được format")
    void convertsBareDate() {
        assertThat(NotificationService.formatLegacyDates("Ngày vào ở 2026-11-05"))
                .isEqualTo("Ngày vào ở 05/11/2026");
    }

    @Test
    @DisplayName("Chuỗi đã format hoặc không có ngày thì giữ nguyên")
    void leavesOtherTextUntouched() {
        assertThat(NotificationService.formatLegacyDates("Hợp đồng đã ký ngày 05/11/2026"))
                .isEqualTo("Hợp đồng đã ký ngày 05/11/2026");
        assertThat(NotificationService.formatLegacyDates("Không có ngày")).isEqualTo("Không có ngày");
        assertThat(NotificationService.formatLegacyDates(null)).isNull();
        assertThat(NotificationService.formatLegacyDates("")).isEmpty();
    }
}
