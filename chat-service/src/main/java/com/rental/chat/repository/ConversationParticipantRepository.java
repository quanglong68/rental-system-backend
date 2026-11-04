package com.rental.chat.repository;

import com.rental.chat.entity.ConversationParticipant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, Long> {

    Optional<ConversationParticipant> findByConversationIdAndUserId(Long conversationId, Long userId);

    List<ConversationParticipant> findByConversationId(Long conversationId);

    List<ConversationParticipant> findByConversationIdIn(Collection<Long> conversationIds);

    @Query("""
            select distinct p2.userId from ConversationParticipant p1, ConversationParticipant p2
            where p1.conversationId = p2.conversationId and p1.userId = :userId and p2.userId <> :userId
            """)
    List<Long> findPeerIds(@Param("userId") Long userId);

    @Query("select coalesce(sum(p.unreadCount), 0) from ConversationParticipant p where p.userId = :userId")
    long totalUnread(@Param("userId") Long userId);

    @Modifying
    @Query("""
            update ConversationParticipant p set p.unreadCount = p.unreadCount + 1
            where p.conversationId = :conversationId and p.userId <> :senderId
            """)
    int incrementUnread(@Param("conversationId") Long conversationId, @Param("senderId") Long senderId);

    @Modifying
    @Query("""
            update ConversationParticipant p set p.unreadCount = 0, p.lastReadMessageId = :messageId
            where p.conversationId = :conversationId and p.userId = :userId
            """)
    int markRead(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
            @Param("messageId") Long messageId);

    @Modifying
    @Query("update ConversationParticipant p set p.lastSeenAt = :at where p.userId = :userId")
    int touchLastSeen(@Param("userId") Long userId, @Param("at") java.time.Instant at);
}
