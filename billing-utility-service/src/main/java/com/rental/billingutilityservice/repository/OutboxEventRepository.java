package com.rental.billingutilityservice.repository;

import com.rental.billingutilityservice.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();

    boolean existsByEventTypeAndEventKeyAndCreatedAtGreaterThanEqual(String eventType, String eventKey,
            Instant createdAt);
}
