package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.entity.ContractInfo;
import com.rental.billingutilityservice.repository.ContractInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Giu ban sao contract_info (docs muc 9.5): cap nhat tu ContractSigned, ContractRenewed,
 * ContractEnded. Dung de kiem tra gan dich vu + phan quyen theo toa nha.
 */
@Service
@RequiredArgsConstructor
public class ContractInfoService {

    private final ContractInfoRepository repository;

    @Transactional
    public void upsert(Long contractId, Long roomId, Long buildingId, String status, LocalDate startDate,
            LocalDate endDate) {
        if (contractId == null || contractId <= 0) {
            return;
        }
        ContractInfo info = repository.findById(contractId).orElseGet(() -> {
            ContractInfo created = new ContractInfo();
            created.setContractId(contractId);
            return created;
        });
        // Renewed/Ended co the thieu room/building: giu gia tri cu, khong ghi de bang 0 (docs 9.5).
        if (roomId != null && roomId > 0) {
            info.setRoomId(roomId);
        }
        if (buildingId != null && buildingId > 0) {
            info.setBuildingId(buildingId);
        }
        if (status != null) {
            info.setStatus(status);
        }
        if (startDate != null) {
            info.setStartDate(startDate);
        }
        if (endDate != null) {
            info.setEndDate(endDate);
        }
        repository.save(info);
    }
}
