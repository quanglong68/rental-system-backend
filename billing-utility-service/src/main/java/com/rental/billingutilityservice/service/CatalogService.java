package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.dto.request.AttachServiceRequest;
import com.rental.billingutilityservice.dto.request.BuildingServiceRequest;
import com.rental.billingutilityservice.dto.response.BuildingServiceResponse;
import com.rental.billingutilityservice.dto.response.ContractServiceResponse;
import com.rental.billingutilityservice.entity.BuildingService;
import com.rental.billingutilityservice.entity.ContractInfo;
import com.rental.billingutilityservice.entity.ContractService;
import com.rental.billingutilityservice.enums.UsageType;
import com.rental.billingutilityservice.repository.BuildingServiceRepository;
import com.rental.billingutilityservice.repository.ContractInfoRepository;
import com.rental.billingutilityservice.repository.ContractServiceRepository;
import com.rental.billingutilityservice.support.AuthSupport;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Dich vu toa nha + dich vu gan hop dong (docs muc 9.5): QUAN_LY quan ly bang gia theo toa nha;
 * gan dich vu vao hop dong voi SHARED/PERSONAL (PERSONAL bat buoc contractTenantId,
 * 422 TENANT_REQUIRED_FOR_PERSONAL).
 */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final BuildingServiceRepository services;
    private final ContractServiceRepository contractServices;
    private final ContractInfoRepository contractInfos;
    private final AuthSupport auth;

    @Transactional
    public BuildingServiceResponse createService(BuildingServiceRequest request) {
        auth.requireBuildingScope(request.getBuildingId());
        BuildingService entity = new BuildingService();
        entity.setBuildingId(request.getBuildingId());
        entity.setName(request.getName().trim());
        entity.setUnit(request.getUnit() == null || request.getUnit().isBlank() ? "month" : request.getUnit().trim());
        entity.setUnitPrice(request.getUnitPrice());
        return toServiceResponse(services.save(entity));
    }

    @Transactional
    public BuildingServiceResponse updateService(Long id, BuildingServiceRequest request) {
        BuildingService entity = services.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Khong tim thay dich vu"));
        auth.requireBuildingScope(entity.getBuildingId());
        if (request.getBuildingId() != null && !request.getBuildingId().equals(entity.getBuildingId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Khong duoc chuyen dich vu sang toa nha khac");
        }
        entity.setName(request.getName().trim());
        entity.setUnit(request.getUnit() == null || request.getUnit().isBlank() ? "month" : request.getUnit().trim());
        entity.setUnitPrice(request.getUnitPrice());
        return toServiceResponse(services.save(entity));
    }

    @Transactional(readOnly = true)
    public List<BuildingServiceResponse> listServices(Long buildingId) {
        if (buildingId != null) {
            auth.requireBuildingScope(buildingId);
            return services.findByBuildingId(buildingId).stream().map(this::toServiceResponse).toList();
        }
        if (auth.isAdmin()) {
            return services.findAll().stream().map(this::toServiceResponse).toList();
        }
        List<Long> allowedIds;
        try {
            allowedIds = auth.staffBuildingIds(auth.currentUserId());
        } catch (RuntimeException e) {
            allowedIds = List.of();
        }
        final List<Long> allowed = allowedIds;
        return services.findAll().stream().filter(s -> allowed.contains(s.getBuildingId()))
                .map(this::toServiceResponse).toList();
    }

    @Transactional
    public ContractServiceResponse attach(AttachServiceRequest request) {
        BuildingService service = services.findById(request.getServiceId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Khong tim thay dich vu"));
        ContractInfo contract = contractInfos.findById(request.getContractId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Khong tim thay hop dong"));
        auth.requireBuildingScope(contract.getBuildingId());
        if (!service.getBuildingId().equals(contract.getBuildingId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Dich vu khong thuoc toa nha cua hop dong");
        }
        UsageType usageType;
        try {
            usageType = UsageType.valueOf(request.getUsageType());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "usageType phai la SHARED hoac PERSONAL");
        }
        if (usageType == UsageType.PERSONAL && request.getContractTenantId() == null) {
            throw new BusinessException("TENANT_REQUIRED_FOR_PERSONAL",
                    "Dich vu ca nhan bat buoc co contractTenantId", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        ContractService entity = new ContractService();
        entity.setContractId(request.getContractId());
        entity.setService(service);
        entity.setServiceName(service.getName());
        entity.setQuantity(request.getQuantity() == null ? 1 : request.getQuantity());
        entity.setUsageType(usageType);
        entity.setContractTenantId(request.getContractTenantId());
        return toContractServiceResponse(contractServices.save(entity));
    }

    @Transactional(readOnly = true)
    public List<ContractServiceResponse> listByContract(Long contractId) {
        ContractInfo contract = contractInfos.findById(contractId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Khong tim thay hop dong"));
        auth.requireBuildingScope(contract.getBuildingId());
        return contractServices.findByContractId(contractId).stream().map(this::toContractServiceResponse).toList();
    }

    @Transactional
    public void detach(Long id) {
        ContractService entity = contractServices.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Khong tim thay dich vu gan hop dong"));
        contractInfos.findById(entity.getContractId()).ifPresent(c -> auth.requireBuildingScope(c.getBuildingId()));
        contractServices.delete(entity);
    }

    private BuildingServiceResponse toServiceResponse(BuildingService entity) {
        return BuildingServiceResponse.builder().id(entity.getId()).buildingId(entity.getBuildingId())
                .name(entity.getName()).unit(entity.getUnit()).unitPrice(entity.getUnitPrice()).build();
    }

    private ContractServiceResponse toContractServiceResponse(ContractService entity) {
        return ContractServiceResponse.builder().id(entity.getId()).contractId(entity.getContractId())
                .serviceId(entity.getService().getId()).serviceName(entity.getServiceName())
                .quantity(entity.getQuantity()).usageType(entity.getUsageType().name())
                .contractTenantId(entity.getContractTenantId()).build();
    }
}
