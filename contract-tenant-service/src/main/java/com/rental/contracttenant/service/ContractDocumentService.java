package com.rental.contracttenant.service;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractDocument;
import com.rental.contracttenant.entity.ContractSignature;
import com.rental.contracttenant.entity.ContractTenant;
import com.rental.contracttenant.repository.ContractDocumentRepository;
import com.rental.contracttenant.repository.ContractSignatureRepository;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Generates a new PDF version, stores it privately and records its SHA-256 hash. */
@Service
@RequiredArgsConstructor
public class ContractDocumentService {

    private final ContractDocumentRepository documents;
    private final ContractSignatureRepository signatures;
    private final ContractDocumentStorage storage;
    private final ContractPdfRenderer renderer;

    public ContractDocument generate(Contract contract, List<ContractTenant> tenants) {
        List<ContractSignature> signatureRows = signatures.findByContractIdOrderBySignedAtAsc(contract.getId());
        Map<com.rental.contracttenant.entity.SignatureParty, ContractPdfRenderer.SignatureView> views =
                ContractPdfRenderer.signatureViews(signatureRows, storage::readBytes);
        byte[] pdf = renderer.render(contract, tenants, views);
        return store(contract.getId(), pdf, "CONTRACT");
    }

    /** Spec 13.6: an annex recording a member joining, kept next to the contract versions. */
    public ContractDocument generateAddendum(Contract contract, List<ContractTenant> tenants, ContractTenant newMember) {
        byte[] pdf = renderer.renderAddendum(contract, tenants, newMember);
        return store(contract.getId(), pdf, "ADDENDUM");
    }

    private ContractDocument store(Long contractId, byte[] pdf, String documentType) {
        int version = documents.findMaxVersion(contractId) + 1;
        String objectKey = storage.uploadPdf(contractId, version, pdf);
        ContractDocument document = new ContractDocument();
        document.setContractId(contractId);
        document.setVersion(version);
        document.setDocumentType(documentType);
        document.setObjectKey(objectKey);
        document.setSha256(sha256(pdf));
        return documents.save(document);
    }

    public ContractDocument latest(Long contractId) {
        return documents.findFirstByContractIdAndDocumentTypeOrderByVersionDesc(contractId, "CONTRACT")
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Hợp đồng chưa có tài liệu PDF"));
    }

    public ContractDocument latestAddendum(Long contractId) {
        return documents.findFirstByContractIdAndDocumentTypeOrderByVersionDesc(contractId, "ADDENDUM")
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "Hợp đồng chưa có phụ lục PDF"));
    }

    /** Detail pages must still load when PDF generation failed; null means "not generated yet". */
    public ContractDocument latestOrNull(Long contractId) {
        return documents.findFirstByContractIdAndDocumentTypeOrderByVersionDesc(contractId, "CONTRACT")
                .orElse(null);
    }

    public static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (Exception ex) {
            throw new IllegalStateException("Không tính được SHA-256", ex);
        }
    }
}
