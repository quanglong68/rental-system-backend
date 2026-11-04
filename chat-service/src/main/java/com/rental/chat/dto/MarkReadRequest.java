package com.rental.chat.dto;

import jakarta.validation.constraints.NotNull;

public record MarkReadRequest(@NotNull(message = "Thiếu mã tin nhắn đã đọc") Long messageId) {
}
