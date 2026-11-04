package com.rental.contracttenant.repository;

import com.rental.contracttenant.entity.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Relay scan: at most 100 unpublished rows in insert order (docs muc 5.3). Ordering by
     * {@code sequence_no} rather than {@code created_at} keeps events written in one transaction in
     * the order the domain emitted them, which the strict per-contract revision stream requires.
     */
    @Query(value = "SELECT * FROM outbox_event WHERE published_at IS NULL ORDER BY sequence_no LIMIT 100",
            nativeQuery = true)
    List<OutboxEvent> findPendingInOrder();

    boolean existsByEventTypeAndEventKey(String eventType, String eventKey);
}
