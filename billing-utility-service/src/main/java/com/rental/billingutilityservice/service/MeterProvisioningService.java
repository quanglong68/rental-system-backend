package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.entity.Meter;
import com.rental.billingutilityservice.entity.UtilityType;
import com.rental.billingutilityservice.repository.MeterRepository;
import com.rental.billingutilityservice.repository.UtilityTypeRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tu dong khoi tao dong ho dien/nuoc voi chi so random khac 0 cho phong moi ky hop dong
 * (mo phong dong ho thuc te da qua su dung, khong reset ve 0).
 */
@Service
@RequiredArgsConstructor
public class MeterProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(MeterProvisioningService.class);

    private final MeterRepository meters;
    private final UtilityTypeRepository utilityTypes;

    @Transactional
    public List<Meter> ensureDefaultMetersForRoom(Long roomId, Long buildingId) {
        if (roomId == null || roomId <= 0 || buildingId == null || buildingId <= 0) {
            return List.of();
        }
        List<Meter> existing = meters.findByRoomId(roomId);
        boolean hasElectric = existing.stream().anyMatch(m -> !isWater(m.getUtilityType()));
        boolean hasWater = existing.stream().anyMatch(m -> isWater(m.getUtilityType()));
        if (hasElectric && hasWater) {
            return existing.stream().filter(Meter::isActive).toList();
        }

        List<UtilityType> allTypes = utilityTypes.findAll();
        if (!hasElectric) {
            UtilityType electric = resolveElectricType(allTypes);
            createRandomMeter(roomId, buildingId, electric, false);
        }
        if (!hasWater) {
            UtilityType water = resolveWaterType(allTypes);
            createRandomMeter(roomId, buildingId, water, true);
        }
        return meters.findByRoomIdAndIsActiveTrue(roomId);
    }

    private void createRandomMeter(Long roomId, Long buildingId, UtilityType type, boolean water) {
        Meter meter = new Meter();
        meter.setRoomId(roomId);
        meter.setBuildingId(buildingId);
        meter.setUtilityType(type);
        meter.setMeterCode(buildMeterCode(roomId, water));
        int initial = water
                ? ThreadLocalRandom.current().nextInt(18, 351)
                : ThreadLocalRandom.current().nextInt(180, 2501);
        meter.setInitialIndex(BigDecimal.valueOf(initial));
        meter.setInitialIndexDate(null);
        meter.setActive(true);
        try {
            meters.save(meter);
            log.info("Tự tạo đồng hồ {} cho phòng {} (tòa {}), chỉ số khởi tạo {}",
                    water ? "nước" : "điện", roomId, buildingId, initial);
        } catch (DataIntegrityViolationException duplicate) {
            log.debug("Đồng hồ {} phòng {} đã tồn tại khi tạo đồng thời", water ? "nước" : "điện", roomId);
        }
    }

    private UtilityType resolveElectricType(List<UtilityType> existing) {
        return existing.stream()
                .filter(ut -> !isWater(ut))
                .findFirst()
                .orElseGet(() -> createUtilityType("Điện sinh hoạt", "kWh", new BigDecimal("3500.00")));
    }

    private UtilityType resolveWaterType(List<UtilityType> existing) {
        return existing.stream()
                .filter(MeterProvisioningService::isWater)
                .findFirst()
                .orElseGet(() -> createUtilityType("Nước sinh hoạt", "m3", new BigDecimal("12000.00")));
    }

    private UtilityType createUtilityType(String defaultName, String unit, BigDecimal price) {
        return utilityTypes.findByNameIgnoreCase(defaultName).orElseGet(() -> {
            UtilityType ut = new UtilityType();
            ut.setName(defaultName);
            ut.setUnit(unit);
            ut.setUnitPrice(price);
            try {
                return utilityTypes.save(ut);
            } catch (DataIntegrityViolationException ex) {
                return utilityTypes.findByNameIgnoreCase(defaultName).orElseThrow(() -> ex);
            }
        });
    }

    private static String buildMeterCode(Long roomId, boolean water) {
        String raw = "AUTO-" + roomId + (water ? "-W" : "-E");
        return raw.length() <= 50 ? raw : raw.substring(0, 50);
    }

    public static boolean isWater(UtilityType type) {
        if (type == null) {
            return false;
        }
        String name = type.getName() == null ? "" : type.getName().toLowerCase();
        String unit = type.getUnit() == null ? "" : type.getUnit().toLowerCase();
        return name.contains("nước") || name.contains("nuoc") || name.contains("water") || unit.contains("m3");
    }
}

