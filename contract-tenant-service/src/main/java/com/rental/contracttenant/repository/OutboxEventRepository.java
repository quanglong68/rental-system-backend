package com.rental.contracttenant.repository;

import com.rental.contracttenant.entity.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /** Relay scan: at most 100 unpublished rows, oldest first (docs muc 5.3). */
    List<OutboxEvent> findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
}
