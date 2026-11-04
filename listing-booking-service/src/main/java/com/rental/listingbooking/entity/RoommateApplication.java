package com.rental.listingbooking.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "roommate_application", uniqueConstraints = {
    @UniqueConstraint(name = "uq_post_customer", columnNames = {"roommate_post_id", "customer_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoommateApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "roommate_post_id", nullable = false)
    private Long roommatePostId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "full_name", length = 200)
    private String fullName;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "cccd_number", length = 20)
    private String cccdNumber;

    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;

    @Column(name = "gender", length = 30)
    private String gender;

    @Column(name = "cccd_issue_date")
    private java.time.LocalDate cccdIssueDate;

    @Column(name = "cccd_issue_place", length = 255)
    private String cccdIssuePlace;

    @Column(name = "permanent_address", length = 1000)
    private String permanentAddress;

    @Column(name = "occupation", length = 200)
    private String occupation;

    @Column(name = "move_in_date")
    private java.time.LocalDate moveInDate;

    @Column(name = "cccd_front_key", length = 500)
    private String cccdFrontKey;

    @Column(name = "cccd_back_key", length = 500)
    private String cccdBackKey;

    @Column(name = "status", nullable = false)
    private String status; // PENDING, ACCEPTED_PENDING, ACCEPTED, REJECTED, REJECTED_FULL

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
        if (status == null) status = "PENDING";
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
