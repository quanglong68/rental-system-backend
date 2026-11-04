package com.rental.billingutilityservice.entity;

import com.rental.billingutilityservice.enums.InvoiceStatus;
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
 * Hoa don tong cua phong cho mot thang (docs muc 9.5, 11): tong hop cac hoa don
 * tung nguoi o (share) de quan ly va khach thay hoa don ca phong. Tong tien va
 * trang thai duoc tinh lai tu cac share moi khi sinh hoa don / ghi nhan thanh toan.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "room_invoice", uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "month", "year"}))
public class RoomInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(nullable = false)
    private Integer month;

    @Column(nullable = false)
    private Integer year;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "paid_amount", nullable = false, precision = 15, scale = 0)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "rent_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal rentAmount = BigDecimal.ZERO;

    @Column(name = "electric_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal electricAmount = BigDecimal.ZERO;

    @Column(name = "water_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal waterAmount = BigDecimal.ZERO;

    @Column(name = "service_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal serviceAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status = InvoiceStatus.UNPAID;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
