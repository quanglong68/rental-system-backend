package com.rental.chat.dto;

public record ContactResponse(
        Long userId,
        String fullName,
        String position,
        boolean manager,
        boolean assigned) {
}
