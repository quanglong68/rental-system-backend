package com.rental.user.dto;

import java.time.Instant;

public record NotificationResponse(Long id, String type, String title, String content,
        String refType, Long refId, boolean isRead, Instant createdAt) { }
