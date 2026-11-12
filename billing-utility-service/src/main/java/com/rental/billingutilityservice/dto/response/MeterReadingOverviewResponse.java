package com.rental.billingutilityservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Tinh trang chi so cua mot ky: phong cho/da ban giao (hop dong moi) va danh sach
 * phong thuoc ky chot so kem trang thai hoa don.
 */
@Value
@Builder
public class MeterReadingOverviewResponse {
    int month;
    int year;
    int dayOfMonth;
    boolean firstFiveDays;
    boolean endOfMonthReminder;
    boolean futurePeriod;
    boolean contractsAvailable;
    List<HandoverRoom> handoverRooms;
    List<MissingRoom> missingRooms;
    List<ClosingRoom> closingRooms;

    @Value
    @Builder
    public static class HandoverRoom {
        Long contractId;
        Long roomId;
        Long buildingId;
        String roomNumber;
        LocalDate moveInDate;
        boolean completed;
        LocalDate handoverDate;
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
    public static class ClosingRoom {
        Long roomId;
        Long buildingId;
        String roomNumber;
        boolean missingReading;
        boolean incomplete;
        boolean hasExistingReadings;
        boolean hasInvoice;
        boolean editable;
        String invoicePaymentStatus;
        List<MeterItem> meters;
    }

    @Value
    @Builder
    public static class MeterItem {
        Long meterId;
        Long utilityTypeId;
        String utilityTypeName;
        String unit;
        BigDecimal unitPrice;
        BigDecimal initialIndex;
        LocalDate initialIndexDate;
        Long readingId;
        BigDecimal prevIndex;
        String prevIndexSource;
        BigDecimal currentIndex;
    }
}
