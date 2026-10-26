package com.rental.contracttenant.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of {@code PUT /api/v1/contracts/{id}/tenants/{tenantId}/leave} (spec 9.4: {@code {leftAt}}).
 */
@Getter
@Setter
@NoArgsConstructor
public class TenantLeaveRequest {

    @NotNull
    private LocalDate leftAt;
}
