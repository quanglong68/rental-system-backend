package com.rental.billingutilityservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Ghi chi so ban giao cho mot phong: cap nhat moc initialIndex cua tung dong ho. */
@Data
public class SaveHandoverRequest {

    @NotNull
    private Long roomId;

    private LocalDate readingDate;

    @NotEmpty
    @Valid
    private List<MeterIndex> readings;

    @Data
    public static class MeterIndex {
        @NotNull
        private Long meterId;
        @NotNull
        private BigDecimal currentIndex;
    }
}
