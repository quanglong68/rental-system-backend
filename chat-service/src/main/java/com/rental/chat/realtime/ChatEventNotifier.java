package com.rental.chat.realtime;

import com.rental.chat.event.ChatEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Su kien nghiep vu chi duoc gui sau khi transaction commit (tranh thong bao tin nhan bi rollback). */
@Component
@RequiredArgsConstructor
public class ChatEventNotifier {

    private final ApplicationEventPublisher publisher;
    private final SseRegistry registry;

    public void publish(java.util.Collection<Long> userIds, String event, Object payload) {
        publisher.publishEvent(new ChatEvent(userIds, event, payload));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void deliver(ChatEvent event) {
        registry.sendToUsers(event.userIds(), event.event(), event.payload());
    }
}
