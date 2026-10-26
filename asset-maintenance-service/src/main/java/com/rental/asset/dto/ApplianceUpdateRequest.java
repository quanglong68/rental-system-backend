package com.rental.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ApplianceUpdateRequest(
        @NotBlank String name,
        @Pattern(regexp = "GOOD|NEEDS_REPAIR|BROKEN") String status) {}
