package com.rental.contracttenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** One immutable version of the generated contract PDF. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "contract_document")
public class ContractDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Column(nullable = false)
    private int version;

    /** CONTRACT for the signed agreement, ADDENDUM for a member-join annex. */
    @Column(name = "document_type", nullable = false, length = 20)
    private String documentType = "CONTRACT";

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(nullable = false, length = 64)
    private String sha256;

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;
}
