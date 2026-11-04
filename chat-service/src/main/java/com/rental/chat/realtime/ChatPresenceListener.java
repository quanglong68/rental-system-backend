package com.rental.chat.realtime;

import com.rental.chat.dto.PresenceEvent;
import com.rental.chat.event.UserOfflineEvent;
import com.rental.chat.event.UserOnlineEvent;
import com.rental.chat.repository.ConversationParticipantRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChatPresenceListener {

    private final ConversationParticipantRepository participants;
    private final SseRegistry registry;

    @EventListener
    @Transactional
    public void online(UserOnlineEvent event) {
        List<Long> peers = participants.findPeerIds(event.userId());
        registry.sendToUsers(peers, "presence", new PresenceEvent(event.userId(), true, null));
    }

    @EventListener
    @Transactional
    public void offline(UserOfflineEvent event) {
        Instant now = Instant.now();
        participants.touchLastSeen(event.userId(), now);
        List<Long> peers = participants.findPeerIds(event.userId());
        registry.sendToUsers(peers, "presence", new PresenceEvent(event.userId(), false, now));
    }
}
