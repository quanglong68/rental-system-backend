package com.rental.chat.realtime;

import com.rental.chat.dto.ReadyEvent;
import com.rental.chat.event.UserOfflineEvent;
import com.rental.chat.event.UserOnlineEvent;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Giu cac ket noi SSE trong bo nho (MVP mot instance). Moi user co the mo nhieu tab;
 * su kien duoc phat cho tat ca emitter cua user nhan.
 */
@Component
public class SseRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseRegistry.class);

    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher events;

    @Value("${chat.sse.timeout-ms:1800000}")
    private long timeoutMs;

    @Value("${chat.sse.max-connections-per-user:3}")
    private int maxConnectionsPerUser;

    public SseRegistry(ApplicationEventPublisher events) {
        this.events = events;
    }

    public SseEmitter connect(Long userId) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        Set<SseEmitter> userEmitters = emitters.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet());
        boolean firstConnection = userEmitters.isEmpty();
        userEmitters.add(emitter);
        if (userEmitters.size() > maxConnectionsPerUser) {
            userEmitters.stream().filter(existing -> existing != emitter).findFirst().ifPresent(oldest -> {
                remove(userId, oldest);
                oldest.complete();
            });
        }
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> {
            remove(userId, emitter);
            emitter.complete();
        });
        emitter.onError(error -> remove(userId, emitter));

        try {
            emitter.send(SseEmitter.event().name("ready").data(new ReadyEvent(userId), MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            remove(userId, emitter);
            emitter.completeWithError(ex);
            return emitter;
        }
        if (firstConnection) {
            events.publishEvent(new UserOnlineEvent(userId));
        }
        log.debug("SSE connected userId={} tabs={}", userId, userEmitters.size());
        return emitter;
    }

    public boolean isOnline(Long userId) {
        Set<SseEmitter> userEmitters = emitters.get(userId);
        return userEmitters != null && !userEmitters.isEmpty();
    }

    public void sendToUsers(Collection<Long> userIds, String event, Object payload) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        userIds.forEach(userId -> sendToUser(userId, event, payload));
    }

    public void sendToUser(Long userId, String event, Object payload) {
        Set<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name(event).data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException ex) {
                remove(userId, emitter);
                emitter.complete();
            }
        }
    }

    @Scheduled(fixedDelayString = "${chat.sse.heartbeat-ms:20000}")
    public void heartbeat() {
        emitters.forEach((userId, userEmitters) -> {
            for (SseEmitter emitter : userEmitters) {
                try {
                    emitter.send(SseEmitter.event().comment("keepalive"));
                } catch (IOException | IllegalStateException ex) {
                    remove(userId, emitter);
                    emitter.complete();
                }
            }
        });
    }

    private void remove(Long userId, SseEmitter emitter) {
        Set<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) {
            return;
        }
        boolean removed = userEmitters.remove(emitter);
        if (removed && userEmitters.isEmpty()) {
            emitters.remove(userId, userEmitters);
            events.publishEvent(new UserOfflineEvent(userId));
            log.debug("SSE disconnected userId={}", userId);
        }
    }
}
