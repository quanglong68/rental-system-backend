package com.rental.chat.dto;

import java.time.Instant;

public record ConversationReadEvent(Long conversationId, Long readerId, String readerName, Long messageId,
        Instant readAt) {
}
