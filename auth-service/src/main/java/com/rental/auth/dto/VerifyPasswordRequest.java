package com.rental.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Internal request from contract-tenant-service when a party confirms an electronic signature. */
public record VerifyPasswordRequest(@NotNull Long accountId, @NotBlank String password) {
}
