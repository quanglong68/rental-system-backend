package com.rental.billingutilityservice.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Split Billing Engine — cong thuc muc 11 (tinh theo ngay, chia theo dau nguoi).
 * Moi ngay d, n(d) = so nguoi co mat; phan chi phi ngay do (T/D) chia deu cho n(d) nguoi.
 * Tien nguoi t = sum((T/D)/n(d)) tren cac ngay t co mat.
 * Số ngày ở = giao của [joinedAt, leftAt] + [startDate, endDate] + tháng (tính cả 2 đầu).
 * Trung gian dung BigDecimal (khong double); item scale 2; tong lam tron den hang nghin.
 */
public final class BillingCalculator {

    private BillingCalculator() {
    }

    /** Khoảng hiện diện của một người trong tháng (đã giao với hợp đồng + tháng). */
    public record StayInterval(Long contractTenantId, LocalDate from, LocalDate to) {
    }

    /**
     * Chia tong T cho cac khoang hien dien trong thang.
     *
     * @param monthlyTotal tổng cả tháng (rent_amount, tiêu thụ x đơn giá, giá DV x SL)
     * @param stays khoang hien dien (from/to inclusive, nam trong thang)
     * @param month tháng tính tiền
     * @return tien moi contractTenantId (chua lam tron hang nghin)
     */
    public static Map<Long, BigDecimal> split(BigDecimal monthlyTotal, List<StayInterval> stays, YearMonth month) {
        Map<Long, BigDecimal> result = new HashMap<>();
        stays.forEach(s -> result.put(s.contractTenantId(), BigDecimal.ZERO));
        if (monthlyTotal == null || monthlyTotal.signum() <= 0 || stays.isEmpty()) {
            return result;
        }
        int daysInMonth = month.lengthOfMonth();
        BigDecimal dailyRate = monthlyTotal.divide(BigDecimal.valueOf(daysInMonth), 10, RoundingMode.HALF_UP);
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = month.atDay(day);
            List<StayInterval> present = stays.stream()
                    .filter(s -> !date.isBefore(s.from()) && !date.isAfter(s.to())).toList();
            int n = present.size();
            if (n == 0) {
                // Ngay ngoai hop dong/khong ai o: chu nha chiu (muc 11.5), bo qua.
                continue;
            }
            BigDecimal perPerson = dailyRate.divide(BigDecimal.valueOf(n), 10, RoundingMode.HALF_UP);
            for (StayInterval s : present) {
                result.merge(s.contractTenantId(), perPerson, BigDecimal::add);
            }
        }
        return result;
    }

    /** Giao [joinedAt, leftAt?] + [contractStart, contractEnd] + tháng (inclusive). Null nếu không giao. */
    public static StayInterval intersect(Long contractTenantId, LocalDate joinedAt, LocalDate leftAt,
            LocalDate contractStart, LocalDate contractEnd, YearMonth month) {
        LocalDate from = max(joinedAt, contractStart, month.atDay(1));
        LocalDate end = minOrNull(leftAt, contractEnd, month.atEndOfMonth());
        if (from == null || end == null || from.isAfter(end)) {
            return null;
        }
        return new StayInterval(contractTenantId, from, end);
    }

    /** Lam tron tong hoa don den hang nghin (muc 11.4): chia 1000, HALF_UP 0 chu so, nhan lai 1000. */
    public static BigDecimal roundToThousand(BigDecimal total) {
        if (total == null) {
            return BigDecimal.ZERO;
        }
        return total.divide(BigDecimal.valueOf(1000), 0, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(1000));
    }

    /** Item amount giu 2 chu so thap phan (muc 11.4). */
    public static BigDecimal itemAmount(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate max(LocalDate... dates) {
        LocalDate result = null;
        for (LocalDate d : dates) {
            if (d == null) {
                continue;
            }
            if (result == null || d.isAfter(result)) {
                result = d;
            }
        }
        return result;
    }

    private static LocalDate minOrNull(LocalDate... dates) {
        LocalDate result = null;
        for (LocalDate d : dates) {
            if (d == null) {
                continue;
            }
            if (result == null || d.isBefore(result)) {
                result = d;
            }
        }
        return result;
    }
}
