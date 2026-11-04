package com.rental.billingutilityservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Mot share (hoa don tung nguoi) trong hoa don tong cua phong (docs muc 11.4). */
@Value
@Builder
public class RoomInvoiceShareResponse {
    Long invoiceId;
    Long contractTenantId;
    Long customerId;
    BigDecimal totalAmount;
    BigDecimal paidAmount;
    String status;
    LocalDate dueDate;
    boolean representative;
    /** true khi khach khong phai dai dien: so tien cua nguoi khac bi an. */
    boolean masked;
}
