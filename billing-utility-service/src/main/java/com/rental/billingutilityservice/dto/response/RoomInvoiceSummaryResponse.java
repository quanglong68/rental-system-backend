package com.rental.billingutilityservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** So lieu tong hop hoa don phong theo thang/nam cho trang Tong quan (ADMIN). */
@Value
@Builder
public class RoomInvoiceSummaryResponse {
    int year;
    int month;
    PeriodSummary monthSummary;
    PeriodSummary yearSummary;
    List<MonthlyPoint> monthly;
    List<BuildingSummary> byBuilding;
    List<RoomDebt> topDebtRooms;

    @Value
    @Builder
    public static class PeriodSummary {
        BigDecimal billed;
        BigDecimal paid;
        BigDecimal debt;
        int invoiceCount;
        int paidCount;
        int overdueCount;
        BigDecimal rent;
        BigDecimal electric;
        BigDecimal water;
        BigDecimal service;
    }

    @Value
    @Builder
    public static class MonthlyPoint {
        int month;
        BigDecimal billed;
        BigDecimal paid;
    }

    @Value
    @Builder
    public static class BuildingSummary {
        Long buildingId;
        BigDecimal billed;
        BigDecimal paid;
        BigDecimal debt;
        int invoiceCount;
        int overdueCount;
    }

    @Value
    @Builder
    public static class RoomDebt {
        Long roomInvoiceId;
        Long roomId;
        Long buildingId;
        BigDecimal outstanding;
        LocalDate dueDate;
        String status;
    }
}
