package com.rental.billingutilityservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Tinh trang chi so cua mot ky: phong cho ban giao (hop dong moi) va phong con thieu
 * chi so chot thang. FE dung cung mot nguon de to tab va to do cac dong can nhap.
 */
@Value
@Builder
public class MeterReadingOverviewResponse {
    int month;
    int year;
    int dayOfMonth;
    boolean firstFiveDays;
    boolean contractsAvailable;
    List<HandoverRoom> handoverRooms;
    List<MissingRoom> missingRooms;

    @Value
    @Builder
    public static class HandoverRoom {
        Long contractId;
        Long roomId;
        Long buildingId;
        String roomNumber;
        LocalDate moveInDate;
        List<MeterItem> meters;
    }

    @Value
    @Builder
    public static class MissingRoom {
        Long roomId;
        Long buildingId;
        String roomNumber;
        List<MeterItem> meters;
    }

    @Value
    @Builder
    public static class MeterItem {
        Long meterId;
        String utilityTypeName;
        BigDecimal initialIndex;
        LocalDate initialIndexDate;
        BigDecimal currentIndex;
    }
}
