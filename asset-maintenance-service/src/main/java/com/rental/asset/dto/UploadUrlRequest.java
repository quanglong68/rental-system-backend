package com.rental.asset.dto;

import jakarta.validation.constraints.NotBlank;

public record UploadUrlRequest(@NotBlank String fileName, @NotBlank String contentType) {}
