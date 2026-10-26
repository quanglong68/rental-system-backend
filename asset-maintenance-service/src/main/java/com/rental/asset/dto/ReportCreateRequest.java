package com.rental.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ReportCreateRequest(
        @NotNull Long roomId,
        @NotBlank String title,
        @NotBlank String description,
        @Size(max = 5) List<@NotBlank String> imageUrls) {}
