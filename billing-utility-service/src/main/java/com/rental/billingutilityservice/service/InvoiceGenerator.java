package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.client.BillingDataResponse;
import com.rental.billingutilityservice.client.PropertyClient;
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
    private final RoomInvoiceService roomInvoices;
    private final BillingEventPublisher events;
    private final PropertyClient propertyClient;

    @Value("${billing.due-days:7}")
    private int dueDays;

    /**
     * @return so invoice da sinh (0 neu bo qua: da co hoa don / thieu chi so / khong ai o)
     */
    @Transactional
    public int generateForContract(BillingDataResponse contract, int month, int year,
            List<GenerateInvoiceResult.SkippedRoom> skipped) {
        return generateForContract(contract, month, year, skipped, false, true);
    }

    /**
     * @param refreshUnpaid  khi true: hóa đơn UNPAID/OVERDUE chưa thu tiền được tính lại theo
     *                       chỉ số mới; khi false: giữ idempotent "chạy lại không sinh trùng".
     * @param publishMissing khi true: phát MeterReadingMissing khi phòng thiếu chỉ số.
     *                       Auto-save chỉ số truyền false để không spam thông báo giữa chừng.
     */
    @Transactional
    public int generateForContract(BillingDataResponse contract, int month, int year,
            List<GenerateInvoiceResult.SkippedRoom> skipped, boolean refreshUnpaid, boolean publishMissing) {
        YearMonth target = YearMonth.of(year, month);
        List<BillingDataResponse.TenantStay> stays = contract.getTenants() == null ? List.of()
                : contract.getTenants();
        if (stays.isEmpty()) {
            return 0;
        }
        Map<Long, Invoice> existingByTenant = new HashMap<>();
        if (refreshUnpaid) {
            for (Invoice invoice : invoices.findByRoomIdAndMonthAndYear(contract.getRoomId(), month, year)) {
                if (invoice.getContractId() != null && invoice.getContractId().equals(contract.getContractId())) {
                    existingByTenant.put(invoice.getContractTenantId(), invoice);
                }
            }
        }
        // Idempotent: người đã có hóa đơn tháng này không tạo mới; chỉ tính lại hóa đơn chưa thu.
        List<BillingDataResponse.TenantStay> pending = stays.stream()
                .filter(t -> !existingByTenant.containsKey(t.getContractTenantId()))
                .filter(t -> refreshUnpaid
                        || !invoices.existsByContractTenantIdAndMonthAndYear(t.getContractTenantId(), month, year))
                .toList();
        List<BillingDataResponse.TenantStay> refreshable = new ArrayList<>();
        for (BillingDataResponse.TenantStay stay : stays) {
            Invoice existing = existingByTenant.get(stay.getContractTenantId());
            if (existing == null) {
                continue;
            }
            if (canRefresh(existing)) {
                refreshable.add(stay);
            } else {
                skipped.add(GenerateInvoiceResult.SkippedRoom.builder().roomId(contract.getRoomId())
                        .reason("INVOICE_ALREADY_PAID").build());
            }
        }
        if (pending.isEmpty() && refreshable.isEmpty()) {
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
            if (publishMissing) {
                events.publishMeterReadingMissing(contract.getRoomId(), contract.getBuildingId(),
                        roomNumber(contract.getRoomId(), contract.getRoomNumber()), month, year, missingMeterIds);
            }
            return 0;
        }
        // Khoang hien dien moi nguoi (giao hop dong + thang, tinh ca 2 dau - muc 11.2).
        // Dung TOAN BO stays de tinh n(d) (muc 11.3); chi loc target khi sinh invoice.
        // Neu chi dung target thi chay lai tung phan se chia sai mau so.
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
        List<BillingDataResponse.TenantStay> targets = new ArrayList<>(pending);
        targets.addAll(refreshable);
        java.util.Set<Long> targetIds = targets.stream()
                .map(BillingDataResponse.TenantStay::getContractTenantId).collect(java.util.stream.Collectors.toSet());
        List<StayInterval> intervals = allIntervals.stream()
                .filter(i -> targetIds.contains(i.contractTenantId())).toList();
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
        LocalDate dueDate;
        if (contract.getPaymentDueDay() != null && contract.getPaymentDueDay() >= 1 && contract.getPaymentDueDay() <= 31) {
            YearMonth dueMonth = target.plusMonths(1);
            int targetDay = Math.min(contract.getPaymentDueDay(), dueMonth.lengthOfMonth());
            LocalDate candidateDue = dueMonth.atDay(targetDay);
            dueDate = candidateDue.isBefore(LocalDate.now(ZoneOffset.UTC))
                    ? LocalDate.now(ZoneOffset.UTC).plusDays(dueDays)
                    : candidateDue;
        } else {
            dueDate = LocalDate.now(ZoneOffset.UTC).plusDays(dueDays);
        }
        Instant now = Instant.now();
        RoomInvoice master = null;
        for (BillingDataResponse.TenantStay t : targets) {
            boolean present = intervals.stream()
                    .anyMatch(i -> i.contractTenantId().equals(t.getContractTenantId()));
            if (!present) {
                continue; // Nguoi khong o ngay nao trong thang: khong sinh hoa don.
            }
            List<ItemDraft> drafts = new ArrayList<>();
            BigDecimal rentShare = rentShares.getOrDefault(t.getContractTenantId(), BigDecimal.ZERO);
            if (rentShare.signum() > 0) {
                drafts.add(new ItemDraft(InvoiceItemType.RENT, "Tiền phòng tháng " + month + "/" + year,
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
                        (type == InvoiceItemType.WATER ? "Tiền nước" : "Tiền điện") + " tháng " + month + "/"
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
                            cs.getServiceName() + " (cá nhân) tháng " + month + "/" + year, total, null, cs.getId()));
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
                            cs.getServiceName() + " (chung) tháng " + month + "/" + year, share, null, cs.getId()));
                }
            }
            BigDecimal rawTotal = drafts.stream().map(ItemDraft::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (rawTotal.signum() <= 0) {
                continue;
            }
            Invoice existing = existingByTenant.get(t.getContractTenantId());
            Invoice invoice = existing == null ? new Invoice() : existing;
            boolean created = existing == null;
            if (master == null) {
                master = roomInvoices.getOrCreate(contract.getRoomId(), contract.getBuildingId(), month, year,
                        dueDate);
            }
            invoice.setRoomInvoiceId(master.getId());
            invoice.setContractId(contract.getContractId());
            invoice.setRoomId(contract.getRoomId());
            invoice.setBuildingId(contract.getBuildingId());
            invoice.setContractTenantId(t.getContractTenantId());
            invoice.setCustomerId(t.getCustomerId());
            invoice.setRepresentative(Boolean.TRUE.equals(t.getRepresentative()));
            invoice.setMonth(month);
            invoice.setYear(year);
            invoice.setTotalAmount(BillingCalculator.roundToThousand(rawTotal));
            if (created) {
                invoice.setPaidAmount(BigDecimal.ZERO);
                invoice.setStatus(InvoiceStatus.UNPAID);
                invoice.setIssuedAt(now);
                invoice.setDueDate(dueDate);
                invoice.setOverdueNotified(false);
            } else {
                items.deleteByInvoice_Id(invoice.getId());
            }
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
            if (created) {
                events.publishInvoiceIssued(saved.getId(), saved.getCustomerId(), saved.getContractId(),
                        saved.getRoomId(), saved.getBuildingId(), saved.getRoomInvoiceId(), month, year,
                        saved.getTotalAmount(), saved.getDueDate());
            }
            count++;
        }
        if (master != null) {
            roomInvoices.recompute(master);
        }
        return count;
    }

    private boolean canRefresh(Invoice invoice) {
        return invoice.getStatus() != InvoiceStatus.PAID
                && invoice.getStatus() != InvoiceStatus.PARTIALLY_PAID
                && (invoice.getPaidAmount() == null || invoice.getPaidAmount().signum() == 0);
    }

    static boolean isWater(Meter meter) {
        String name = meter.getUtilityType().getName() == null ? "" : meter.getUtilityType().getName().toLowerCase();
        String unit = meter.getUtilityType().getUnit() == null ? "" : meter.getUtilityType().getUnit().toLowerCase();
        return name.contains("nuoc") || name.contains("nước") || name.contains("water") || unit.contains("m3");
    }

    /**
     * So phong de thong bao de hieu; loi goi Property khong duoc lam hong viec sinh hoa don.
     * Uu tien roomNumber da co trong billing-data de khong phai goi Feign moi lan.
     */
    private String roomNumber(Long roomId, String knownRoomNumber) {
        if (knownRoomNumber != null && !knownRoomNumber.isBlank()) {
            return knownRoomNumber;
        }
        try {
            PropertyClient.RoomInternal room = propertyClient.getRoom(roomId);
            return room == null ? null : room.getRoomNumber();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    record ItemDraft(InvoiceItemType type, String description, BigDecimal amount, Long meterReadingId,
            Long contractServiceId) {
    }
}
