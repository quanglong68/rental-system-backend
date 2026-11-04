package com.rental.chat.repository;

import com.rental.chat.entity.Conversation;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByDedupKey(String dedupKey);

    @Query(value = """
            select c from Conversation c
            where c.id in (select p.conversationId from ConversationParticipant p where p.userId = :userId)
              and (:keyword is null or lower(c.contextLabel) like :keyword)
            order by coalesce(c.lastMessageAt, c.createdAt) desc, c.id desc
            """,
            countQuery = """
            select count(c) from Conversation c
            where c.id in (select p.conversationId from ConversationParticipant p where p.userId = :userId)
              and (:keyword is null or lower(c.contextLabel) like :keyword)
            """)
    Page<Conversation> findForUser(@Param("userId") Long userId, @Param("keyword") String keyword,
            Pageable pageable);
}
