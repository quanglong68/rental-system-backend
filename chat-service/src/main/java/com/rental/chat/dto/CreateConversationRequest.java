package com.rental.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Tao hoac mo hoi thoai theo ngu canh nghiep vu.
 * {@code staffId} bat buoc khi ngu canh chua gan nhan vien (xem phong/dat phong/quan ly toa).
 * {@code customerId} chi dung khi nhan vien chu dong mo hoi thoai voi khach.
 */
public record CreateConversationRequest(
        @NotBlank(message = "Thiếu loại hội thoại") String contextType,
        @NotNull(message = "Thiếu mã ngữ cảnh") Long contextId,
        Long staffId,
        Long customerId) {
}
