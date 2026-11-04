package com.rental.listingbooking.outbox;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Terms and identity snapshots travel with the accept event so contract-tenant-service can build the
 * draft and render the PDF without calling back into listing or property data that may change later.
 */
public record RentalRequestAcceptedPayload(
    Long rentalRequestId,
    Long roomId,
    Long buildingId,
    Long customerId,
    BigDecimal price,
    LocalDate moveInDate,
    BigDecimal depositAmount,
    Integer minTermMonths,
    String roomNumber,
    String buildingName,
    String roomAddress,
    BigDecimal electricityPrice,
    BigDecimal waterPrice,
    String waterUnit,
    String paymentMethod,
    Integer paymentDueDay,
    LocalDate availableFrom,
    String lessorName,
    LocalDate lessorDateOfBirth,
    String lessorIdNumber,
    LocalDate lessorIdIssueDate,
    String lessorIdIssuePlace,
    String lessorPhone,
    String lessorAddress,
    String tenantFullName,
    LocalDate tenantDateOfBirth,
    String tenantGender,
    String tenantPhone,
    String tenantEmail,
    String tenantPermanentAddress,
    String tenantOccupation,
    String tenantCccdNumber,
    LocalDate tenantCccdIssueDate,
    String tenantCccdIssuePlace,
    String tenantCccdFrontKey,
    String tenantCccdBackKey
) {}
