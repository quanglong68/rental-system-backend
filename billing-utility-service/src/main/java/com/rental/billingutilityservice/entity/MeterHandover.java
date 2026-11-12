package com.rental.billingutilityservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Trang thai ghi chi so ban giao theo hop dong: luu lai moc da ban giao
 * (ke ca khi KTV ghi truoc start_date) de tab Ban giao khach moi danh dau da xong ma khong bien mat.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "meter_handover")
public class MeterHandover {

    @Id
    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(name = "handover_date", nullable = false)
    private LocalDate handoverDate;

    @Column(name = "entered_by")
    private Long enteredBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

