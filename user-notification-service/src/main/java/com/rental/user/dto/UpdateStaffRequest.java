package com.rental.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateStaffRequest(@NotBlank @Size(max = 160) String fullName,
        @Size(max = 32) String phone, @Email @Size(max = 320) String email,
        @Size(max = 120) String position) { }
