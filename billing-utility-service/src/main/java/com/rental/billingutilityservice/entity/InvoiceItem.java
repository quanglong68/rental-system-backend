package com.rental.billingutilityservice.entity;

import com.rental.billingutilityservice.enums.InvoiceItemType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Dong hoa don (docs muc 9.5, 11): amount luu thap phan 2 chu so; tong cuoi lam tron
 * den hang nghin o invoice.total_amount. meterReadingId la quan he nhieu-mot.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "invoice_item")
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 20)
    private InvoiceItemType itemType;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "meter_reading_id")
    private Long meterReadingId;

    @Column(name = "contract_service_id")
    private Long contractServiceId;
}
