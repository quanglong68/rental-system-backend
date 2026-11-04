package com.rental.chat.dto;

import java.time.Instant;

public record MessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        String senderType,
        String senderName,
        String content,
        String clientMessageId,
        Instant createdAt) {
}
