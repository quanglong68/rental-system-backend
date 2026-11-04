package com.rental.contracttenant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Negotiable draft terms QUAN_LY may repair before signing (spec 9.4 "Chốt giá thuê").
 *
 * <p>Deliberately EXCLUDES the identity fields (rentalRequestId, roomId, buildingId, customerId)
 * and status: a client of this endpoint has nothing to corrupt them with (see
 * {@code ContractService.updateDraftDetails}). Terminated contracts only ever need
 * {@code terminationReason}, set by the future terminate endpoint, not here.
 *
 * <p>Term fields are optional and keep their stored value when omitted, so an edit that only
 * changes the rent does not wipe the electricity/water/payment terms.
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

    @DecimalMin(value = "0", inclusive = true)
    @Digits(integer = 15, fraction = 0)
    private BigDecimal electricityPrice;

    @DecimalMin(value = "0", inclusive = true)
    @Digits(integer = 15, fraction = 0)
    private BigDecimal waterPrice;

    @Pattern(regexp = "M3|PERSON", message = "Đơn vị nước phải là M3 hoặc PERSON")
    private String waterUnit;

    @Pattern(regexp = "CASH|BANK_TRANSFER", message = "Hình thức thanh toán phải là CASH hoặc BANK_TRANSFER")
    private String paymentMethod;

    @Min(value = 1, message = "Ngày đến hạn phải từ 1 đến 28")
    @Max(value = 28, message = "Ngày đến hạn phải từ 1 đến 28")
    private Integer paymentDueDay;

    @Positive(message = "Kỳ hạn tối thiểu phải lớn hơn 0")
    private Integer minTermMonths;

    // ---- Full editable snapshot. The manager must be able to correct every printed contract
    // field (room/building address and both parties' identity), because the printed PDF is the
    // legal document and stale/missing values make it unusable. ----
    private String roomNumber;
    private String buildingName;
    private String roomAddress;
    private LocalDate availableFrom;

    private String lessorName;
    private LocalDate lessorDateOfBirth;
    private String lessorIdNumber;
    private LocalDate lessorIdIssueDate;
    private String lessorIdIssuePlace;
    private String lessorPhone;
    private String lessorAddress;

    private String tenantFullName;
    private LocalDate tenantDateOfBirth;
    private String tenantGender;
    private String tenantPhone;
    private String tenantEmail;
    private String tenantPermanentAddress;
    private String tenantOccupation;
    private String tenantCccdNumber;
    private LocalDate tenantCccdIssueDate;
    private String tenantCccdIssuePlace;
}
