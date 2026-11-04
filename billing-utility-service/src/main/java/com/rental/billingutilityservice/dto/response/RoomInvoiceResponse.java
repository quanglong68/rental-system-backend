package com.rental.billingutilityservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Hoa don tong cua phong/thang kem danh sach share tung nguoi (docs muc 9.5, 11.4). */
@Value
@Builder
public class RoomInvoiceResponse {
    Long id;
    Long roomId;
    Long buildingId;
    Integer month;
    Integer year;
    BigDecimal totalAmount;
    BigDecimal paidAmount;
    BigDecimal rentAmount;
    BigDecimal electricAmount;
    BigDecimal waterAmount;
    BigDecimal serviceAmount;
    String status;
    Instant issuedAt;
    LocalDate dueDate;
    int shareCount;
    List<RoomInvoiceShareResponse> shares;
}
