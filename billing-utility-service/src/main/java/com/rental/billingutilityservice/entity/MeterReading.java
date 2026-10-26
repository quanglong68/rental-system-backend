package com.rental.billingutilityservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Chi so chot tung thang (docs muc 9.5): prevIndex tu thang truoc (hoac initialIndex),
 * consumption = currentIndex - prevIndex (>= 0), amount = consumption x unit_price hien hanh.
 * Moi dong ho moi thang mot dong; sua duoc cho den khi thang do da sinh hoa don.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "meter_reading", uniqueConstraints = @UniqueConstraint(columnNames = {"meter_id", "month", "year"}))
public class MeterReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meter_id", nullable = false)
    private Meter meter;

    @Column(nullable = false)
    private Integer month;

    @Column(nullable = false)
    private Integer year;

    @Column(name = "reading_date", nullable = false)
    private LocalDate readingDate;

    @Column(name = "prev_index", nullable = false, precision = 15, scale = 2)
    private BigDecimal prevIndex;

    @Column(name = "current_index", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentIndex;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal consumption;

    @Column(name = "unit_price", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "entered_by")
    private Long enteredBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
