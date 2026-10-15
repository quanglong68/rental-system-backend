package com.rental.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "staff")
@Getter @Setter @NoArgsConstructor
public class Staff {
    @Id private Long id;
    @Column(name = "full_name", length = 160) private String fullName;
    @Column(length = 32) private String phone;
    @Column(length = 320) private String email;
    @Column(length = 120) private String position;
    @Column(name = "is_admin", nullable = false) private boolean admin;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @PrePersist void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}
