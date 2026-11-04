package com.rental.propertiesservice.features.room.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Spec 9.2: {@code PUT /api/v1/rooms/{id}/status} body is {@code {status:"MAINTENANCE"|"VACANT"}}. */
public record ChangeRoomStatusRequest(@NotBlank(message = "status là bắt buộc") String status) {
}
