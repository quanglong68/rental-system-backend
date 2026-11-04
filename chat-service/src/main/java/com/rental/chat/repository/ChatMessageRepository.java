package com.rental.chat.repository;

import com.rental.chat.entity.ChatMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByIdDesc(Long conversationId, Pageable pageable);

    List<ChatMessage> findByConversationIdAndIdLessThanOrderByIdDesc(Long conversationId, Long beforeId,
            Pageable pageable);

    Optional<ChatMessage> findByIdAndConversationId(Long id, Long conversationId);

    Optional<ChatMessage> findByConversationIdAndClientMessageId(Long conversationId, String clientMessageId);

    Optional<ChatMessage> findTopByConversationIdOrderByIdDesc(Long conversationId);
}
