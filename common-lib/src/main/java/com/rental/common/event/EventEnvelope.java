package com.rental.common.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Envelope chuan cho moi Kafka event (docs muc 5.3).
 * Du lieu tren topic la chuoi JSON dang nay; consumer doc eventType roi chuyen cho handler
 * tuong ung, bo qua eventType minh khong quan tam.
 * Event bat bien va tuong thich nguoc: chi duoc them field moi, khong doi ten/xoa field (docs muc 1).
 *
 * @param <T> kieu payload cu the cua tung event (vi du AccountRegisteredPayload)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventEnvelope<T> {

    /** UUID moi event, dung cho idempotent consumer (bang processed_event). */
    private UUID eventId;

    /** Ten event, vi du AccountRegistered, ContractSigned. */
    private String eventType;

    /** Version event, bat dau tu 1; doi lon thi tang version thay vi sua field cu. */
    private int version;

    /** Thoi diem xay ra, UTC ISO-8601 (vi du 2026-10-03T08:00:00Z). */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant occurredAt;

    /** X-Correlation-Id de truy vet xuyen service. */
    private String correlationId;

    /** Payload phang: ID la so, tenantIds[] la danh sach customerId (docs muc 10). */
    private T payload;

    /**
     * Tao envelope version 1 voi eventId ngau nhien va occurredAt = hien tai (UTC).
     *
     * @param eventType ten event
     * @param payload payload cua event
     * @param correlationId correlation id cua request goc (co the null)
     * @param <T> kieu payload
     * @return envelope san sang serialize sang JSON
     */
    public static <T> EventEnvelope<T> of(String eventType, T payload, String correlationId) {
        return EventEnvelope.<T>builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .version(1)
                .occurredAt(Instant.now())
                .correlationId(correlationId)
                .payload(payload)
                .build();
    }
}
