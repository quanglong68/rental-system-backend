package com.rental.auth.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rental.auth.entity.AccountType;
import com.rental.auth.repository.OutboxEventRepository;
import com.rental.auth.entity.OutboxEvent;
import com.rental.common.constant.Headers;
import com.rental.common.event.EventEnvelope;
import com.rental.common.security.HeaderAuthenticationFilter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Ghi event vao bang outbox CUNG giao dich nghiep vu (docs muc 5.3).
 * CAM goi kafkaTemplate.send truc tiep trong AccountService: neu crash giua chung
 * se mat event; ghi outbox thi relay se gui lai.
 */
@Service
@RequiredArgsConstructor
public class OutboxEventPublisher {

    /** Topic auth-service phat event tai khoan (ke hoach da chot Q4). */
    public static final String AUTH_EVENTS_TOPIC = "auth.events";

    /** Ten event dang ky tai khoan (docs Table 16). */
    public static final String EVENT_ACCOUNT_REGISTERED = "AccountRegistered";

    private final OutboxEventRepository outbox;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Ghi 1 dong AccountRegistered. Goi trong ham @Transactional cua AccountService
     * de chung commit/rollback voi tao account.
     */
    public void publishAccountRegistered(Long accountId, AccountType accountType, String username, String fullName,
            String email, String phone) {
        AccountRegisteredPayload payload = new AccountRegisteredPayload();
        payload.setAccountId(accountId);
        payload.setAccountType(accountType.name());
        payload.setUsername(username);
        payload.setFullName(fullName);
        payload.setEmail(email);
        payload.setPhone(phone);
        EventEnvelope<AccountRegisteredPayload> envelope = EventEnvelope.of(EVENT_ACCOUNT_REGISTERED, payload,
                correlationId());
        OutboxEvent event = new OutboxEvent();
        event.setId(envelope.getEventId());
        event.setTopic(AUTH_EVENTS_TOPIC);
        event.setEventKey(String.valueOf(accountId));
        event.setEventType(EVENT_ACCOUNT_REGISTERED);
        try {
            event.setPayload(objectMapper.writeValueAsString(envelope));
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Khong serialize duoc AccountRegistered", impossible);
        }
        outbox.save(event);
    }

    private static String correlationId() {
        String fromMdc = MDC.get(HeaderAuthenticationFilter.MDC_CORRELATION_ID);
        if (fromMdc != null && !fromMdc.isBlank()) {
            return fromMdc;
        }
        String fromHeader = null;
        try {
            var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
                fromHeader = servlet.getRequest().getHeader(Headers.X_CORRELATION_ID);
            }
        } catch (Exception ignored) {
            // Khong de truy vet lam vo nghiep vu chinh.
        }
        return fromHeader != null && !fromHeader.isBlank() ? fromHeader : UUID.randomUUID().toString();
    }
}