package com.rental.chat.dto;

import java.time.Instant;

public record ConversationSummaryResponse(
        Long id,
        String contextType,
        String contextLabel,
        Long peerId,
        String peerName,
        String peerType,
        String peerRole,
        String lastMessagePreview,
        Instant lastMessageAt,
        long unreadCount,
        boolean online,
        Instant lastSeenAt,
        Long peerLastReadMessageId) {
}
