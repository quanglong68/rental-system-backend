package com.rental.billingutilityservice.scheduler;

import com.rental.billingutilityservice.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Scheduler billing (docs muc 9.5): chot so 02:00 ngay closing-day (mac dinh ngay 1, gio VN)
 * cho thang truoc; quet overdue 03:00 hang ngay. Feign tu scheduler gui X-User-Type SYSTEM
 * (FeignHeaderInterceptor). Contract loi -> job dung, ghi log, staff chay lai tay.
 */
@Component
@RequiredArgsConstructor
public class BillingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingScheduler.class);
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final InvoiceService invoices;

    @Value("${billing.closing-day:1}")
    private int closingDay;

    /** Chot so thang truoc luc 02:00 ngay closing-day (gio VN). */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Ho_Chi_Minh")
    public void closeMonth() {
        LocalDate today = LocalDate.now(VN);
        if (today.getDayOfMonth() != closingDay) {
            return;
        }
        YearMonth previous = YearMonth.from(today).minusMonths(1);
        try {
            var result = invoices.generate(previous.getMonthValue(), previous.getYear(), null);
            log.info("Chot so {}/{}: {} hoa don, {} phong bo qua", previous.getMonthValue(), previous.getYear(),
                    result.getGeneratedCount(), result.getSkipped().size());
        } catch (Exception e) {
            log.error("Chot so {}/{} that bai, staff chay lai tay: {}", previous.getMonthValue(), previous.getYear(),
                    e.toString());
        }
    }

    /** Quet overdue 03:00 hang ngay (gio VN), phat InvoiceOverdue mot lan. */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Ho_Chi_Minh")
    public void sweepOverdue() {
        try {
            int count = invoices.markOverdue(LocalDate.now(VN));
            if (count > 0) {
                log.info("Danh dau {} hoa don OVERDUE", count);
            }
        } catch (Exception e) {
            log.error("Quet overdue that bai: {}", e.toString());
        }
    }
}
