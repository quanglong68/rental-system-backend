package com.rental.contracttenant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RejectRenewalRequest {

    @NotBlank(message = "Lý do từ chối là bắt buộc")
    private String reason;
}
