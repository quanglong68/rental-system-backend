package com.rental.contracttenant.dto;

import com.rental.contracttenant.entity.Contract;
import com.rental.contracttenant.entity.ContractStatus;
import com.rental.contracttenant.entity.ContractTenant;
import com.rental.contracttenant.entity.SignatureStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class ContractDetailResponse {

    Long id;
    Long rentalRequestId;
    Long roomId;
    Long buildingId;
    Long customerId;
    ContractStatus status;
    SignatureStatus signatureStatus;
    Instant signatureDeadline;
    LocalDate startDate;
    LocalDate endDate;
    BigDecimal rentAmount;
    BigDecimal depositAmount;
    String terminationReason;
    Instant createdAt;
    Long renewedFromContractId;
    Long renewalRequestId;

    String roomNumber;
    String buildingName;
    String roomAddress;
    BigDecimal electricityPrice;
    BigDecimal waterPrice;
    String waterUnit;
    String paymentMethod;
    Integer paymentDueDay;
    Integer minTermMonths;
    LocalDate availableFrom;

    String lessorName;
    LocalDate lessorDateOfBirth;
    String lessorIdNumber;
    LocalDate lessorIdIssueDate;
    String lessorIdIssuePlace;
    String lessorPhone;
    String lessorAddress;

    String tenantFullName;
    LocalDate tenantDateOfBirth;
    String tenantGender;
    String tenantPhone;
    String tenantEmail;
    String tenantPermanentAddress;
    String tenantOccupation;
    String tenantCccdNumber;
    LocalDate tenantCccdIssueDate;
    String tenantCccdIssuePlace;

    List<ContractTenantResponse> tenants;
    List<ContractSignatureResponse> signatures;
    ContractDocumentResponse latestDocument;

    boolean depositPaid;
    Instant depositPaidAt;
    String depositPaymentMethod;
    String depositTxnRef;

    public ContractDetailResponse(Long id, Long rentalRequestId, Long roomId, Long buildingId, Long customerId,
            ContractStatus status, SignatureStatus signatureStatus, Instant signatureDeadline, LocalDate startDate,
            LocalDate endDate, BigDecimal rentAmount, BigDecimal depositAmount, String terminationReason,
            Instant createdAt, Long renewedFromContractId, Long renewalRequestId, String roomNumber,
            String buildingName, String roomAddress, BigDecimal electricityPrice, BigDecimal waterPrice,
            String waterUnit, String paymentMethod, Integer paymentDueDay, Integer minTermMonths,
            LocalDate availableFrom, String lessorName, LocalDate lessorDateOfBirth, String lessorIdNumber,
            LocalDate lessorIdIssueDate, String lessorIdIssuePlace, String lessorPhone, String lessorAddress,
            String tenantFullName, LocalDate tenantDateOfBirth, String tenantGender, String tenantPhone,
            String tenantEmail, String tenantPermanentAddress, String tenantOccupation, String tenantCccdNumber,
            LocalDate tenantCccdIssueDate, String tenantCccdIssuePlace, List<ContractTenantResponse> tenants,
            List<ContractSignatureResponse> signatures, ContractDocumentResponse latestDocument) {
        this(id, rentalRequestId, roomId, buildingId, customerId, status, signatureStatus, signatureDeadline,
                startDate, endDate, rentAmount, depositAmount, terminationReason, createdAt,
                renewedFromContractId, renewalRequestId, roomNumber, buildingName, roomAddress, electricityPrice,
                waterPrice, waterUnit, paymentMethod, paymentDueDay, minTermMonths, availableFrom, lessorName,
                lessorDateOfBirth, lessorIdNumber, lessorIdIssueDate, lessorIdIssuePlace, lessorPhone, lessorAddress,
                tenantFullName, tenantDateOfBirth, tenantGender, tenantPhone, tenantEmail, tenantPermanentAddress,
                tenantOccupation, tenantCccdNumber, tenantCccdIssueDate, tenantCccdIssuePlace, tenants, signatures,
                latestDocument, false, null, null, null);
    }

    public static ContractDetailResponse from(Contract contract, List<ContractTenant> tenants,
            List<ContractSignatureResponse> signatures, ContractDocumentResponse latestDocument) {
        List<ContractTenantResponse> tenantResponses = tenants == null ? List.of()
                : tenants.stream().map(ContractTenantResponse::from).toList();
        return new ContractDetailResponse(
                contract.getId(),
                contract.getRentalRequestId(),
                contract.getRoomId(),
                contract.getBuildingId(),
                contract.getCustomerId(),
                contract.getStatus(),
                contract.getSignatureStatus(),
                contract.getSignatureDeadline(),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getRentAmount(),
                contract.getDepositAmount(),
                contract.getTerminationReason(),
                contract.getCreatedAt(),
                contract.getRenewedFromContractId(),
                contract.getRenewalRequestId(),
                contract.getRoomNumber(),
                contract.getBuildingName(),
                contract.getRoomAddress(),
                contract.getElectricityPrice(),
                contract.getWaterPrice(),
                contract.getWaterUnit(),
                contract.getPaymentMethod(),
                contract.getPaymentDueDay(),
                contract.getMinTermMonths(),
                contract.getAvailableFrom(),
                contract.getLessorName(),
                contract.getLessorDateOfBirth(),
                contract.getLessorIdNumber(),
                contract.getLessorIdIssueDate(),
                contract.getLessorIdIssuePlace(),
                contract.getLessorPhone(),
                contract.getLessorAddress(),
                contract.getTenantFullName(),
                contract.getTenantDateOfBirth(),
                contract.getTenantGender(),
                contract.getTenantPhone(),
                contract.getTenantEmail(),
                contract.getTenantPermanentAddress(),
                contract.getTenantOccupation(),
                contract.getTenantCccdNumber(),
                contract.getTenantCccdIssueDate(),
                contract.getTenantCccdIssuePlace(),
                tenantResponses,
                signatures == null ? List.of() : signatures,
                latestDocument,
                contract.isDepositPaid(),
                contract.getDepositPaidAt(),
                contract.getDepositPaymentMethod(),
                contract.getDepositTxnRef()
        );
    }
}
