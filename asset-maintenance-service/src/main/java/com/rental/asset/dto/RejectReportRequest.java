package com.rental.asset.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectReportRequest(@NotBlank String reason) {}
