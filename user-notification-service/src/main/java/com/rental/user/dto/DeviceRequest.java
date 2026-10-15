package com.rental.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeviceRequest(@NotBlank @Size(max = 4096) String token,
        @NotBlank @Pattern(regexp = "IOS|ANDROID|WEB") String platform) { }
