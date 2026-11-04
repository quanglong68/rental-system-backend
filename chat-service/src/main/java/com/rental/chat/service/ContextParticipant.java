package com.rental.chat.service;

public record ContextParticipant(Long userId, String userType, String role, String displayName) {
}
