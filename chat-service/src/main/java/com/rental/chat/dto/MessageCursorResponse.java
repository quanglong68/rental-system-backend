package com.rental.chat.dto;

import java.util.List;

public record MessageCursorResponse(
        List<MessageResponse> content,
        Long nextCursor,
        boolean hasMore,
        Long peerLastReadMessageId) {
}
