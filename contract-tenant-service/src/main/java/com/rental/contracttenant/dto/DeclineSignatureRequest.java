package com.rental.contracttenant.dto;

import jakarta.validation.constraints.Size;

public record DeclineSignatureRequest(@Size(max = 1000) String reason) {
}
