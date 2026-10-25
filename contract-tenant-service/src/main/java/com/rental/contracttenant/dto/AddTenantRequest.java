package com.rental.contracttenant.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of {@code POST /api/v1/contracts/{id}/tenants} (spec 9.4 API row: {@code {customerId, joinedAt}}).
 *
 * <p>Unresolved in the specification, accepted as documented here rather than invented:
 * <ul>
 *   <li>no rule that {@code joinedAt} falls inside the contract term. The schema only enforces
 *       {@code left_at IS NULL OR left_at >= joined_at} (V2), and spec 11.2 counts occupancy days
 *       inclusively, so a date outside the term is not rejected here;</li>
 *   <li>no rule that {@code joinedAt} may not be in the past;</li>
 *   <li>{@code customerId} is a User-service identifier; nothing in this service can verify that
 *       the customer exists, so only positivity is checked.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
public class AddTenantRequest {

    @NotNull
    @Positive
    private Long customerId;

    @NotNull
    private LocalDate joinedAt;
}
