package com.rental.billingutilityservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Response hoa don kem items (docs muc 9.5, 11). */
@Value
@Builder
public class InvoiceResponse {
    Long id;
    Long contractId;
    Long roomId;
    Long buildingId;
    Long contractTenantId;
    Long customerId;
    Integer month;
    Integer year;
    BigDecimal totalAmount;
    BigDecimal paidAmount;
    String status;
    Instant issuedAt;
    LocalDate dueDate;
    List<InvoiceItemResponse> items;
}
