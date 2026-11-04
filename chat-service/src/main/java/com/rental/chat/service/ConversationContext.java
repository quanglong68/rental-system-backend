package com.rental.chat.service;

public record ConversationContext(
        String contextType,
        Long contextId,
        String dedupKey,
        String contextLabel,
        Long buildingId,
        Long roomId,
        ContextParticipant self,
        ContextParticipant peer) {
}
