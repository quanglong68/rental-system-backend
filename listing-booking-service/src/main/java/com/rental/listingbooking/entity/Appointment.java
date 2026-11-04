package com.rental.listingbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "appointment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rental_request_id")
    private Long rentalRequestId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "staff_id")
    private Long staffId;  // Optional - null for customer self-booking

    @Column(name = "building_id")
    private Long buildingId;  // Optional - set when SALE confirms appointment

    @Column(name = "room_id")
    private Long roomId;  // Optional - for customer self-booking on specific room

    @Column(name = "appointment_date", nullable = false)
    private Instant appointmentDate;

    @Column(name = "status", nullable = false)
    private String status; // SCHEDULED, VIEWED, CANCELLED, CONFIRMED

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = "SCHEDULED";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void setBuildingId(Long buildingId) {
        this.buildingId = buildingId;
    }

    public void setRoomId(Long roomId) {
        this.roomId = roomId;
    }
}
