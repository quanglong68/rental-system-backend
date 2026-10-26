package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.client.BillingDataResponse;
import com.rental.billingutilityservice.dto.response.GenerateInvoiceResult;
import com.rental.billingutilityservice.entity.*;
import com.rental.billingutilityservice.enums.InvoiceItemType;
import com.rental.billingutilityservice.enums.InvoiceStatus;
import com.rental.billingutilityservice.enums.UsageType;
import com.rental.billingutilityservice.outbox.BillingEventPublisher;
import com.rental.billingutilityservice.repository.*;
import com.rental.billingutilityservice.service.BillingCalculator.StayInterval;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sinh hoa don cho mot hop dong (docs muc 9.5 + 11). Bean rieng de moi hop dong co giao dich
 * doc lap: phong nay loi khong rollback phong khac. Ghi invoice + items + outbox event
 * (InvoiceIssued, MeterReadingMissing) trong cung giao dich (docs muc 5.3).
 */
@Service
@RequiredArgsConstructor
public class InvoiceGenerator {

    private final MeterRepository meters;
    private final MeterReadingRepository readings;
    private final ContractServiceRepository contractServices;
    private final InvoiceRepository invoices;
    private final InvoiceItemRepository items;
    private final BillingEventPublisher events;

    @Value("${billing.due-days:7}")
    private int dueDays;

    /**
     * @return so invoice da sinh (0 neu bo qua: da co hoa don / thieu chi so / khong ai o)
     */
    @Transactional
    public int generateForContract(BillingDataResponse contract, int month, int year,
            List<GenerateInvoiceResult.SkippedRoom> skipped) {
        YearMonth target = YearMonth.of(year, month);
        List<BillingDataResponse.TenantStay> stays = contract.getTenants() == null ? List.of()
                : contract.getTenants();
        if (stays.isEmpty()) {
            return 0;
        }
        // Idempotent: bo qua nguoi da co hoa don thang nay (chay lai khong sinh trung).
        List<BillingDataResponse.TenantStay> pending = stays.stream()
                .filter(t -> !invoices.existsByContractTenantIdAndMonthAndYear(t.getContractTenantId(), month, year))
                .toList();
        if (pending.isEmpty()) {
            return 0;
        }
        // Phong thieu chi so cua bat ky dong ho active -> bo qua + MeterReadingMissing.
        List<Meter> activeMeters = meters.findByRoomIdAndIsActiveTrue(contract.getRoomId());
        Map<Long, MeterReading> readingByMeter = new HashMap<>();
        List<Long> missingMeterIds = new ArrayList<>();
        for (Meter meter : activeMeters) {
            var reading = readings.findByMeter_IdAndMonthAndYear(meter.getId(), month, year);
            if (reading.isEmpty()) {
                missingMeterIds.add(meter.getId());
            } else {
                readingByMeter.put(meter.getId(), reading.get());
            }
        }
        if (!missingMeterIds.isEmpty()) {
            skipped.add(GenerateInvoiceResult.SkippedRoom.builder().roomId(contract.getRoomId())
                    .reason("METER_READING_MISSING").build());
            events.publishMeterReadingMissing(contract.getRoomId(), contract.getBuildingId(), month, year,
                    missingMeterIds);
            return 0;
        }
        // Khoang hien dien moi nguoi (giao hop dong + thang, tinh ca 2 dau - muc 11.2).
        // Dung TOAN BO stays de tinh n(d) (muc 11.3); chi loc pending khi sinh invoice.
        // Neu chi dung pending thi chay lai tung phan se chia sai mau so.
        List<StayInterval> allIntervals = new ArrayList<>();
        for (BillingDataResponse.TenantStay t : stays) {
            StayInterval interval = BillingCalculator.intersect(t.getContractTenantId(), t.getJoinedAt(),
                    t.getLeftAt(), contract.getStartDate(), contract.getEndDate(), target);
            if (interval != null) {
                allIntervals.add(interval);
            }
        }
        if (allIntervals.isEmpty()) {
            return 0;
        }
        java.util.Set<Long> pendingIds = pending.stream()
                .map(BillingDataResponse.TenantStay::getContractTenantId).collect(java.util.stream.Collectors.toSet());
        List<StayInterval> intervals = allIntervals.stream()
                .filter(i -> pendingIds.contains(i.contractTenantId())).toList();
        if (intervals.isEmpty()) {
            return 0;
        }
        // Chia tung khoan theo muc 11.3 (mau so n(d) tinh tren TOAN BO nguoi o).
        BigDecimal rent = contract.getRentAmount() == null ? BigDecimal.ZERO : contract.getRentAmount();
        Map<Long, BigDecimal> rentShares = BillingCalculator.split(rent, allIntervals, target);
        Map<Long, Map<Long, BigDecimal>> utilityShares = new HashMap<>(); // meterId -> tenant -> tien
        for (Meter meter : activeMeters) {
            MeterReading reading = readingByMeter.get(meter.getId());
            BigDecimal total = reading.getConsumption().multiply(reading.getUnitPrice());
            utilityShares.put(meter.getId(), BillingCalculator.split(total, allIntervals, target));
        }
        List<ContractService> attached = contractServices.findByContractId(contract.getContractId());
        Map<Long, Map<Long, BigDecimal>> sharedPerService = new HashMap<>(); // contractServiceId -> tenant -> tien
        for (ContractService cs : attached) {
            if (cs.getUsageType() != UsageType.SHARED) {
                continue;
            }
            BigDecimal price = cs.getService() == null || cs.getService().getUnitPrice() == null ? BigDecimal.ZERO
                    : cs.getService().getUnitPrice();
            BigDecimal total = price.multiply(BigDecimal.valueOf(cs.getQuantity()));
            sharedPerService.put(cs.getId(), BillingCalculator.split(total, allIntervals, target));
        }
        // Sinh invoice moi nguoi.
        int count = 0;
        Instant now = Instant.now();
        LocalDate dueDate = LocalDate.now(ZoneOffset.UTC).plusDays(dueDays);
        for (BillingDataResponse.TenantStay t : pending) {
            boolean present = intervals.stream()
                    .anyMatch(i -> i.contractTenantId().equals(t.getContractTenantId()));
            if (!present) {
                continue; // Nguoi khong o ngay nao trong thang: khong sinh hoa don.
            }
            List<ItemDraft> drafts = new ArrayList<>();
            BigDecimal rentShare = rentShares.getOrDefault(t.getContractTenantId(), BigDecimal.ZERO);
            if (rentShare.signum() > 0) {
                drafts.add(new ItemDraft(InvoiceItemType.RENT, "Tien phong thang " + month + "/" + year,
                        rentShare, null, null));
            }
            for (Meter meter : activeMeters) {
                BigDecimal share = utilityShares.getOrDefault(meter.getId(), Map.of())
                        .getOrDefault(t.getContractTenantId(), BigDecimal.ZERO);
                if (share.signum() <= 0) {
                    continue;
                }
                MeterReading reading = readingByMeter.get(meter.getId());
                InvoiceItemType type = isWater(meter) ? InvoiceItemType.WATER
                        : InvoiceItemType.ELECTRIC;
                drafts.add(new ItemDraft(type,
                        (type == InvoiceItemType.WATER ? "Tien nuoc" : "Tien dien") + " thang " + month + "/"
                                + year + " (" + reading.getConsumption().stripTrailingZeros().toPlainString() + " "
                                + meter.getUtilityType().getUnit() + ")",
                        share, reading.getId(), null));
            }
            for (ContractService cs : attached) {
                // PERSONAL: tron thang cho nguoi dang ky neu co mat >= 1 ngay (muc 11.3).
                if (cs.getUsageType() != UsageType.PERSONAL
                        || !t.getContractTenantId().equals(cs.getContractTenantId())) {
                    continue;
                }
                BigDecimal price = cs.getService() == null || cs.getService().getUnitPrice() == null
                        ? BigDecimal.ZERO
                        : cs.getService().getUnitPrice();
                BigDecimal total = price.multiply(BigDecimal.valueOf(cs.getQuantity()));
                if (total.signum() > 0) {
                    drafts.add(new ItemDraft(InvoiceItemType.SERVICE_PERSONAL,
                            cs.getServiceName() + " (ca nhan) thang " + month + "/" + year, total, null, cs.getId()));
                }
            }
            for (ContractService cs : attached) {
                if (cs.getUsageType() != UsageType.SHARED) {
                    continue;
                }
                BigDecimal share = sharedPerService.getOrDefault(cs.getId(), Map.of())
                        .getOrDefault(t.getContractTenantId(), BigDecimal.ZERO);
                if (share.signum() > 0) {
                    drafts.add(new ItemDraft(InvoiceItemType.SERVICE_SHARED,
                            cs.getServiceName() + " (chung) thang " + month + "/" + year, share, null, cs.getId()));
                }
            }
            BigDecimal rawTotal = drafts.stream().map(ItemDraft::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (rawTotal.signum() <= 0) {
                continue;
            }
            Invoice invoice = new Invoice();
            invoice.setContractId(contract.getContractId());
            invoice.setRoomId(contract.getRoomId());
            invoice.setBuildingId(contract.getBuildingId());
            invoice.setContractTenantId(t.getContractTenantId());
            invoice.setCustomerId(t.getCustomerId());
            invoice.setMonth(month);
            invoice.setYear(year);
            invoice.setTotalAmount(BillingCalculator.roundToThousand(rawTotal));
            invoice.setPaidAmount(BigDecimal.ZERO);
            invoice.setStatus(InvoiceStatus.UNPAID);
            invoice.setIssuedAt(now);
            invoice.setDueDate(dueDate);
            invoice.setOverdueNotified(false);
            Invoice saved = invoices.save(invoice);
            for (ItemDraft draft : drafts) {
                InvoiceItem item = new InvoiceItem();
                item.setInvoice(saved);
                item.setItemType(draft.type());
                item.setDescription(draft.description());
                item.setAmount(BillingCalculator.itemAmount(draft.amount()));
                item.setMeterReadingId(draft.meterReadingId());
                item.setContractServiceId(draft.contractServiceId());
                items.save(item);
            }
            events.publishInvoiceIssued(saved.getId(), saved.getCustomerId(), saved.getContractId(),
                    saved.getRoomId(), saved.getBuildingId(), month, year, saved.getTotalAmount(),
                    saved.getDueDate());
            count++;
        }
        return count;
    }

    static boolean isWater(Meter meter) {
        String name = meter.getUtilityType().getName() == null ? "" : meter.getUtilityType().getName().toLowerCase();
        String unit = meter.getUtilityType().getUnit() == null ? "" : meter.getUtilityType().getUnit().toLowerCase();
        return name.contains("nuoc") || name.contains("nước") || name.contains("water") || unit.contains("m3");
    }

    record ItemDraft(InvoiceItemType type, String description, BigDecimal amount, Long meterReadingId,
            Long contractServiceId) {
    }
}
