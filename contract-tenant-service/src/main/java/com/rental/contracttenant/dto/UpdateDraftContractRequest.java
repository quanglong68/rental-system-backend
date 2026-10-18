package com.rental.contracttenant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Negotiable draft terms QUAN_LY may repair before signing (spec 9.4 "Chốt giá thuê":
 * startDate, endDate, rentAmount, depositAmount).
 *
 * <p>Deliberately EXCLUDES the identity fields (rentalRequestId, roomId, buildingId, customerId)
 * and status: a client of this endpoint has nothing to corrupt them with (see
 * {@code ContractService.updateDraftDetails}). Terminated contracts only ever need
 * {@code terminationReason}, set by the future terminate endpoint, not here.
 *
 * <p>Unresolved in the specification, accepted as documented here rather than invented:
 * <ul>
 *   <li>no maximum bound on rent/deposit (column caps at 10^15 via decimal(15,0));</li>
 *   <li>no rule that startDate must be in the future;</li>
 *   <li>whether depositAmount = 0 is a legitimate business state (accepted as &gt;= 0 here because
 *       the column is nullable and the spec names no minimum).</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
public class UpdateDraftContractRequest {

    @NotNull
    private LocalDate startDate;

    /** May stay null before signing: contract.end_date is nullable and the spec fixes no end date. */
    private LocalDate endDate;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 15, fraction = 0)
    private BigDecimal rentAmount;

    @DecimalMin(value = "0", inclusive = true)
    @Digits(integer = 15, fraction = 0)
    private BigDecimal depositAmount;
}
