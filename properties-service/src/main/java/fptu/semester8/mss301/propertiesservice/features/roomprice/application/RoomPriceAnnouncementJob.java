package fptu.semester8.mss301.propertiesservice.features.roomprice.application;

import fptu.semester8.mss301.propertiesservice.features.events.PropertyEventPublisher;
import fptu.semester8.mss301.propertiesservice.features.roomprice.infrastructure.RoomPriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class RoomPriceAnnouncementJob {
    private final JdbcTemplate jdbc;
    private final RoomPriceRepository prices;
    private final PropertyEventPublisher events;
    private final TransactionTemplate transactions;

    @Scheduled(cron = "${property.outbox.price-announcement-cron}", zone = "Asia/Ho_Chi_Minh")
    public void announceDuePrices() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        for (int batch = 0; batch < 100; batch++) {
            Integer count = transactions.execute(status -> {
                var ids = jdbc.query("""
                        UPDATE room_price SET event_enqueued_at = CURRENT_TIMESTAMP
                        WHERE room_price_id IN (
                            SELECT room_price_id FROM room_price
                            WHERE event_enqueued_at IS NULL AND effective_from <= ?
                              AND (effective_to IS NULL OR effective_to >= ?)
                            ORDER BY effective_from, room_price_id
                            LIMIT 100 FOR UPDATE SKIP LOCKED
                        ) RETURNING room_price_id
                        """, (rs, row) -> rs.getLong(1), today, today);
                ids.forEach(id -> events.roomPriceChanged(prices.findById(id).orElseThrow()));
                return ids.size();
            });
            if (count == null || count < 100) return;
        }
    }
}
