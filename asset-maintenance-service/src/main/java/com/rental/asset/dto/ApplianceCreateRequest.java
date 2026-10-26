package com.rental.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record ApplianceCreateRequest(
        @NotNull Long roomId,
        @NotBlank String name,
        @Pattern(regexp = "GOOD|NEEDS_REPAIR|BROKEN") String status) {}
