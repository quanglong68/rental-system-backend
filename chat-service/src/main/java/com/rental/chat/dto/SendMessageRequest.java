package com.rental.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @NotBlank(message = "Nội dung tin nhắn không được để trống") String content,
        @Size(max = 64, message = "Mã tin nhắn tạm vượt quá 64 ký tự") String clientMessageId) {
}
