package com.rental.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity @Table(name = "processed_event") @Getter @Setter @NoArgsConstructor
public class ProcessedEvent {
    @Id @Column(name = "event_id") private UUID eventId;
    @Column(name = "processed_at", nullable = false) private Instant processedAt = Instant.now();
    public ProcessedEvent(UUID eventId) { this.eventId = eventId; }
}
