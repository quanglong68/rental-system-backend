package com.rental.auth.repository;

import com.rental.auth.entity.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Relay (Task C6) quet toi da 100 dong chua publish theo created_at (docs muc 5.3). */
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
