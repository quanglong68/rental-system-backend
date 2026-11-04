package com.rental.contracttenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Audit row for one party's electronic signature. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "contract_signature")
public class ContractSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_id", nullable = false)
    private Long contractId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SignatureParty party;

    @Column(name = "signer_id", nullable = false)
    private Long signerId;

    @Column(name = "signer_name", length = 200)
    private String signerName;

    @Column(name = "image_key", length = 500)
    private String imageKey;

    @CreationTimestamp
    @Column(name = "signed_at", nullable = false, updatable = false)
    private Instant signedAt;

    @Column(length = 100)
    private String ip;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "pdf_sha256", length = 64)
    private String pdfSha256;

    @Column(name = "consent_version", length = 50)
    private String consentVersion;
}
