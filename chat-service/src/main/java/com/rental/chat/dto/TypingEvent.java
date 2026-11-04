package com.rental.chat.dto;

public record TypingEvent(Long conversationId, Long userId, String userName, boolean typing) {
}
