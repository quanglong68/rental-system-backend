package com.rental.asset.repository;

import com.rental.asset.entity.OutboxEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(
            value = "select * from outbox_event where published_at is null order by occurred_at "
                    + "limit 100 for update skip locked",
            nativeQuery = true)
    List<OutboxEvent> lockPendingEvents();
}
