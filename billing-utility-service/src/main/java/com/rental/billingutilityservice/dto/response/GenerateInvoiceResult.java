package com.rental.billingutilityservice.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/** Ket qua chot so (docs muc 9.5): {month, year, generatedCount, skipped:[{roomId, reason}]}. */
@Value
@Builder
public class GenerateInvoiceResult {
    Integer month;
    Integer year;
    int generatedCount;
    List<SkippedRoom> skipped;

    @Value
    @Builder
    public static class SkippedRoom {
        Long roomId;
        String reason;
    }
}
