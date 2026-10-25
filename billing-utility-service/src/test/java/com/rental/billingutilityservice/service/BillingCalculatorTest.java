package com.rental.billingutilityservice.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Kiem chung cong thuc muc 11: vi du phong 3tr/thang 30 ngay, A o ca thang, B vao ngay 21
 * (10 ngay) -> A 2.5tr, B 500k, tong 3tr.
 */
class BillingCalculatorTest {

    private static final YearMonth SEPTEMBER_30 = YearMonth.of(2026, 9);

    @Test
    void splitRoommatesExampleFromSpec() {
        List<BillingCalculator.StayInterval> stays = List.of(
                new BillingCalculator.StayInterval(1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)),
                new BillingCalculator.StayInterval(2L, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 30)));
        Map<Long, BigDecimal> shares = BillingCalculator.split(new BigDecimal("3000000"), stays, SEPTEMBER_30);
        assertEquals(0, shares.get(1L).compareTo(new BigDecimal("2500000.0000000000")));
        assertEquals(0, shares.get(2L).compareTo(new BigDecimal("500000.0000000000")));
    }

    @Test
    void singleTenantPaysFullMonth() {
        List<BillingCalculator.StayInterval> stays = List.of(
                new BillingCalculator.StayInterval(1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
        Map<Long, BigDecimal> shares = BillingCalculator.split(new BigDecimal("3000000"), stays, SEPTEMBER_30);
        assertEquals(0, shares.get(1L).compareTo(new BigDecimal("3000000.0000000000")));
    }

    @Test
    void daysWithoutTenantGoToLandlord() {
        // Hop dong bat dau ngay 11: 10 ngay dau khong ai o -> tong thu nho hon T.
        List<BillingCalculator.StayInterval> stays = List.of(
                new BillingCalculator.StayInterval(1L, LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 30)));
        Map<Long, BigDecimal> shares = BillingCalculator.split(new BigDecimal("3000000"), stays, SEPTEMBER_30);
        assertEquals(0, shares.get(1L).compareTo(new BigDecimal("2000000.0000000000")));
    }

    @Test
    void intersectCountsBothEndpoints() {
        // Vao ngay 20 thang 30 ngay -> o 11 ngay (20-30); di ngay 10 -> o 10 ngay (1-10).
        BillingCalculator.StayInterval in = BillingCalculator.intersect(1L, LocalDate.of(2026, 9, 20), null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), SEPTEMBER_30);
        assertEquals(LocalDate.of(2026, 9, 20), in.from());
        assertEquals(LocalDate.of(2026, 9, 30), in.to());
        BillingCalculator.StayInterval out = BillingCalculator.intersect(2L, LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), SEPTEMBER_30);
        assertEquals(LocalDate.of(2026, 9, 1), out.from());
        assertEquals(LocalDate.of(2026, 9, 10), out.to());
    }

    @Test
    void roundToThousand() {
        assertEquals(new BigDecimal("2500000"), BillingCalculator.roundToThousand(new BigDecimal("2500400")));
        assertEquals(new BigDecimal("2501000"), BillingCalculator.roundToThousand(new BigDecimal("2500500")));
        assertEquals(new BigDecimal("3000000"), BillingCalculator.roundToThousand(new BigDecimal("3000000")));
    }
}
