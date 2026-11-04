package com.rental.contracttenant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request for direct DRAFT contract creation by SALE/QUAN_LY/ADMIN.
 * No rentalRequestId or listingId - manual workflow, not event-driven.
 */
@Getter
@Setter
@NoArgsConstructor
public class CreateDraftManualRequest {

    @NotNull @Positive
    private Long customerId;

    @NotNull @Positive
    private Long roomId;

    @NotNull @Positive
    private Long buildingId;

    @NotNull
    private LocalDate startDate;

    /** Optional at creation, but a draft cannot be signed until an end date is set. */
    private LocalDate endDate;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Digits(integer = 15, fraction = 0)
    private BigDecimal rentAmount;

    @DecimalMin(value = "0.0", inclusive = true)
    @Digits(integer = 15, fraction = 0)
    private BigDecimal depositAmount;

    // ---- Snapshot used to render the PDF. Optional so the manager can create the draft first and
    // fill the remaining values (or let the service hydrate them from Property/User) before sending. ----
    private String roomNumber;
    private String buildingName;
    private String roomAddress;
    private BigDecimal electricityPrice;
    private BigDecimal waterPrice;
    private String waterUnit;
    private String paymentMethod;
    private Integer paymentDueDay;
    private Integer minTermMonths;
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
