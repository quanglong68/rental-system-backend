package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.dto.request.UtilityTypeRequest;
import com.rental.billingutilityservice.dto.response.UtilityTypeResponse;
import com.rental.billingutilityservice.entity.UtilityType;
import com.rental.billingutilityservice.repository.UtilityTypeRepository;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Loai tien ich (docs muc 9.5): ADMIN tao/sua don gia; gia chi ap dung cho chi so nhap sau
 * (meter_reading snapshot unit_price luc nhap). STAFF xem.
 */
@Service
@RequiredArgsConstructor
public class UtilityTypeService {

    private final UtilityTypeRepository repository;

    @Transactional
    public UtilityTypeResponse create(UtilityTypeRequest request) {
        repository.findByNameIgnoreCase(request.getName().trim()).ifPresent(existing -> {
            throw new BusinessException("UTILITY_TYPE_EXISTS", "Loai tien ich da ton tai",
                    HttpStatus.CONFLICT);
        });
        UtilityType entity = new UtilityType();
        entity.setName(request.getName().trim());
        entity.setUnit(request.getUnit().trim());
        entity.setUnitPrice(request.getUnitPrice());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public UtilityTypeResponse update(Long id, UtilityTypeRequest request) {
        UtilityType entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Khong tim thay loai tien ich"));
        String newName = request.getName().trim();
        repository.findByNameIgnoreCase(newName).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new BusinessException("UTILITY_TYPE_EXISTS", "Loai tien ich da ton tai",
                        HttpStatus.CONFLICT);
            }
        });
        entity.setName(request.getName().trim());
        entity.setUnit(request.getUnit().trim());
        entity.setUnitPrice(request.getUnitPrice());
        return toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<UtilityTypeResponse> list() {        return repository.findAll().stream().map(this::toResponse).toList();
    }

    private UtilityTypeResponse toResponse(UtilityType entity) {
        return UtilityTypeResponse.builder().id(entity.getId()).name(entity.getName()).unit(entity.getUnit())
                .unitPrice(entity.getUnitPrice()).createdAt(entity.getCreatedAt()).build();
    }
}
