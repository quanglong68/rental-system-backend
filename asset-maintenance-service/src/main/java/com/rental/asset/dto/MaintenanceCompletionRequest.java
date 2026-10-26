package com.rental.asset.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public record MaintenanceCompletionRequest(
        @NotBlank String actionTaken,
        @DecimalMin("0.0") BigDecimal cost,
        @Pattern(regexp = "GOOD|NEEDS_REPAIR|BROKEN") String applianceStatus) {}
