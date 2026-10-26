package com.rental.asset.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.OffsetDateTime;

public record ApproveReportRequest(
        Long applianceId,
        @NotNull Long staffId,
        @NotNull @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT") String priority,
        @NotNull OffsetDateTime scheduledAt) {}
