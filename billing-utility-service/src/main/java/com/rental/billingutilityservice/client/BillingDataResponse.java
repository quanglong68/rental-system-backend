package com.rental.billingutilityservice.client;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO billing-data tu Contract (Feign #2, docs muc 5.2, 9.4): hop dong co hieu luc trong thang
 * kem danh sach nguoi o (ngay vao/ra). DTO do ben goi tu khai bao (chi field minh can).
 */
@Data
public class BillingDataResponse {

    private Long contractId;
    private Long roomId;
    private Long buildingId;
    private BigDecimal rentAmount;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private List<TenantStay> tenants;

    @Data
    public static class TenantStay {
        private Long contractTenantId;
        private Long customerId;
        private LocalDate joinedAt;
        private LocalDate leftAt;
        private Boolean representative;
    }
}
