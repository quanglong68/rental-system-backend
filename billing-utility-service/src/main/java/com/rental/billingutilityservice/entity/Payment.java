package com.rental.billingutilityservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/** Thanh toan (docs muc 9.5): staff ghi nhan tien mat; VNPay/MoMo giai doan sau. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "amount_paid", nullable = false, precision = 15, scale = 0)
    private BigDecimal amountPaid;

    @Column(nullable = false, length = 20)
    private String method = "CASH";

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(length = 500)
    private String note;

    @Column(name = "recorded_by", nullable = false)
    private Long recordedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
