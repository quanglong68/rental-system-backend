package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.client.BillingDataResponse;
import com.rental.billingutilityservice.client.ContractClient;
import com.rental.billingutilityservice.dto.response.GenerateInvoiceResult;
import com.rental.billingutilityservice.dto.response.InvoiceItemResponse;
import com.rental.billingutilityservice.dto.response.InvoiceResponse;
import com.rental.common.dto.PageResponse;
import com.rental.billingutilityservice.entity.Invoice;
import com.rental.billingutilityservice.entity.Meter;
import com.rental.billingutilityservice.enums.InvoiceStatus;
import com.rental.billingutilityservice.outbox.BillingEventPublisher;
import com.rental.billingutilityservice.repository.InvoiceItemRepository;
import com.rental.billingutilityservice.repository.InvoiceRepository;
import com.rental.billingutilityservice.repository.MeterReadingRepository;
import com.rental.billingutilityservice.repository.MeterRepository;
import com.rental.billingutilityservice.repository.OutboxEventRepository;
import com.rental.billingutilityservice.support.AuthSupport;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.security.HeaderUser;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Dieu phoi chot so + truy van hoa don (docs muc 9.5): goi Contract (Feign #2) lay hop dong
 * tháng do, uy thac sinh tung hop dong cho {@link InvoiceGenerator} (giao dich doc lap).
 * Khi Contract loi: job dung, ghi log, staff chay lai (khong sinh hoa don thieu).
 */
@Service
@RequiredArgsConstructor
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);

    private final ContractClient contractClient;
    private final InvoiceGenerator generator;
    private final InvoiceRepository invoices;
    private final InvoiceItemRepository items;
    private final RoomInvoiceService roomInvoices;
    private final BillingEventPublisher events;
    private final MeterRepository meters;
    private final MeterReadingRepository readings;
    private final OutboxEventRepository outbox;
    private final AuthSupport auth;

    public GenerateInvoiceResult generate(int month, int year, Long buildingId) {
        YearMonth target = YearMonth.of(year, month);
        if (target.isAfter(YearMonth.now(ZoneOffset.UTC))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không chốt sổ cho tháng tương lai");
        }
        // Staff chi chot so cho toa minh phu trach; ADMIN va scheduler (khong co SecurityContext) chay tat ca.
        List<Long> allowedBuildings = null;
        if (buildingId != null) {
            auth.requireBuildingScope(buildingId);
        } else if (auth.hasAuthenticatedUser() && !auth.isAdmin()) {
            allowedBuildings = auth.staffBuildingIds(auth.currentUserId());
        }
        List<BillingDataResponse> contracts = fetchBillingData(month, year);
        List<GenerateInvoiceResult.SkippedRoom> skipped = new ArrayList<>();
        int generated = 0;
        for (BillingDataResponse contract : contracts) {
            if (buildingId != null && !buildingId.equals(contract.getBuildingId())) {
                continue;
            }
            if (allowedBuildings != null && !allowedBuildings.contains(contract.getBuildingId())) {
                continue;
            }
            try {
                generated += generator.generateForContract(contract, month, year, skipped);
            } catch (BusinessException e) {
                log.warn("Bỏ qua hợp đồng {} phòng {}: {}", contract.getContractId(), contract.getRoomId(),
                        e.getMessage());
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Race idempotent: 2 tien trinh cung sinh -> unique (contract_tenant_id,month,year).
                // Coi nhu da sinh, bo qua de tiep tuc phong khac (docs 9.5: chay lai khong sinh trung).
                log.warn("Trùng hóa đơn hợp đồng {} (race idempotent), bỏ qua: {}", contract.getContractId(),
                        e.getMostSpecificCause() == null ? e.getMessage()
                                : e.getMostSpecificCause().getMessage());
            } catch (RuntimeException e) {
                log.warn("Bỏ qua hợp đồng {} phòng {} do lỗi ngoại lệ: {}", contract.getContractId(),
                        contract.getRoomId(), e.toString());
            }
        }
        return GenerateInvoiceResult.builder().month(month).year(year).generatedCount(generated).skipped(skipped)
                .build();
    }

    /**
     * Nhắc kỹ thuật nhập chỉ số chốt tháng trước mỗi ngày trong 5 ngày đầu tháng. Gom theo tòa
     * để không bắn một thông báo cho từng phòng; mỗi tòa tối đa một thông báo/ngày.
     */
    @Transactional
    public int remindMissingReadings(LocalDate today) {
        if (today.getDayOfMonth() > 5) {
            return 0;
        }
        // Chỉ số cuối tháng được nhập trong 5 ngày đầu tháng kế tiếp nên kỳ cần nhắc là tháng trước.
        YearMonth closing = YearMonth.from(today).minusMonths(1);
        int month = closing.getMonthValue();
        int year = closing.getYear();
        List<BillingDataResponse> contracts = fetchBillingData(month, year);
        java.util.Map<Long, java.util.List<String>> missingByBuilding = new java.util.LinkedHashMap<>();
        for (BillingDataResponse contract : contracts) {
            List<Meter> activeMeters = meters.findByRoomIdAndIsActiveTrue(contract.getRoomId());
            if (activeMeters.isEmpty()) {
                continue;
            }
            boolean missing = activeMeters.stream()
                    .anyMatch(m -> readings.findByMeter_IdAndMonthAndYear(m.getId(), month, year).isEmpty());
            if (!missing) {
                continue;
            }
            String label = contract.getRoomNumber() == null || contract.getRoomNumber().isBlank()
                    ? "#" + contract.getRoomId()
                    : contract.getRoomNumber();
            missingByBuilding.computeIfAbsent(contract.getBuildingId(), id -> new java.util.ArrayList<>()).add(label);
        }
        java.time.Instant startOfDay = today.atStartOfDay(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
        int sent = 0;
        for (java.util.Map.Entry<Long, java.util.List<String>> entry : missingByBuilding.entrySet()) {
            if (outbox.existsByEventTypeAndEventKeyAndCreatedAtGreaterThanEqual(
                    BillingEventPublisher.EVENT_METER_READING_REMINDER, String.valueOf(entry.getKey()), startOfDay)) {
                continue;
            }
            events.publishMeterReadingReminder(entry.getKey(), null, month, year, entry.getValue());
            sent++;
        }
        return sent;
    }

    private List<BillingDataResponse> fetchBillingData(int month, int year) {
        try {
            List<BillingDataResponse> data = contractClient.getBillingData(month, year);
            return data == null ? List.of() : data;
        } catch (FeignException e) {
            log.error("Contract không phản hồi khi chốt sổ {}/{}: {}", month, year, e.toString());
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Contract không phản hồi, thử chạy lại sau");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> search(Long buildingId, Long roomId, Integer month, Integer year,
            String status, Pageable pageable) {
        InvoiceStatus statusEnum = parseStatus(status);
        if (buildingId != null) {
            auth.requireBuildingScope(buildingId);
            return toPage(invoices.search(buildingId, roomId, month, year, statusEnum, pageable), false);
        }
        if (auth.isAdmin()) {
            return toPage(invoices.search(null, roomId, month, year, statusEnum, pageable), false);
        }
        // STAFF chi thay toa minh phu trach (docs 6.2): loc o tang DB de phan trang dung.
        List<Long> allowed = auth.staffBuildingIds(auth.currentUserId());
        if (allowed.isEmpty()) {
            return PageResponse.<InvoiceResponse>builder().content(List.of()).page(pageable.getPageNumber())
                    .size(pageable.getPageSize()).totalElements(0).totalPages(0).build();
        }
        return toPage(invoices.searchIn(allowed, roomId, month, year, statusEnum, pageable), false);
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> mine(Long customerId, Integer month, Integer year, String status,
            Pageable pageable) {
        InvoiceStatus statusEnum = parseStatus(status);
        Page<Invoice> page = invoices.searchMine(customerId, null, null, month, year, statusEnum, pageable);
        return toPage(page, false);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse detail(Long id, HeaderUser principal) {
        Invoice invoice = invoices.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy hóa đơn"));
        boolean admin = isAdmin(principal);
        if (!admin && "CUSTOMER".equalsIgnoreCase(principal.getUserType())
                && !String.valueOf(invoice.getCustomerId()).equals(principal.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Không phải hóa đơn của bạn");
        }
        if (!admin && !"CUSTOMER".equalsIgnoreCase(principal.getUserType())) {
            auth.requireBuildingScope(invoice.getBuildingId());
        }
        return toResponse(invoice, true);
    }

    private PageResponse<InvoiceResponse> toPage(Page<Invoice> page, boolean withItems) {
        return PageResponse.<InvoiceResponse>builder()
                .content(page.getContent().stream().map(i -> toResponse(i, withItems)).toList())
                .page(page.getNumber()).size(page.getSize()).totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages()).build();
    }

    private InvoiceResponse toResponse(Invoice invoice, boolean withItems) {
        List<InvoiceItemResponse> itemResponses = withItems
                ? items.findByInvoice_Id(invoice.getId()).stream()
                        .map(it -> InvoiceItemResponse.builder().id(it.getId()).itemType(it.getItemType().name())
                                .description(it.getDescription()).amount(it.getAmount())
                                .meterReadingId(it.getMeterReadingId()).contractServiceId(it.getContractServiceId())
                                .build())
                        .toList()
                : List.of();
        return InvoiceResponse.builder().id(invoice.getId()).roomInvoiceId(invoice.getRoomInvoiceId())
                .contractId(invoice.getContractId())
                .roomId(invoice.getRoomId()).buildingId(invoice.getBuildingId())
                .contractTenantId(invoice.getContractTenantId()).customerId(invoice.getCustomerId())
                .month(invoice.getMonth()).year(invoice.getYear()).totalAmount(invoice.getTotalAmount())
                .paidAmount(invoice.getPaidAmount()).status(invoice.getStatus().name()).issuedAt(invoice.getIssuedAt())
                .dueDate(invoice.getDueDate()).items(itemResponses).build();
    }

    private boolean isAdmin(HeaderUser principal) {
        return "ADMIN".equalsIgnoreCase(principal.getUserType()) || principal.getRoles().contains("ADMIN");
    }

    private InvoiceStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return InvoiceStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "status phải là UNPAID/PARTIALLY_PAID/PAID/OVERDUE");
        }
    }

    /** Scheduler quet overdue hang ngay: chuyen UNPAID/PARTIALLY_PAID qua han sang OVERDUE + phat 1 lan. */
    @Transactional
    public int markOverdue(LocalDate today) {
        List<Invoice> due = invoices.findByStatusInAndDueDateBefore(
                List.of(InvoiceStatus.UNPAID, InvoiceStatus.PARTIALLY_PAID), today);
        int count = 0;
        for (Invoice invoice : due) {
            invoice.setStatus(InvoiceStatus.OVERDUE);
            invoices.save(invoice);
            if (!invoice.isOverdueNotified()) {
                events.publishInvoiceOverdue(invoice.getId(), invoice.getCustomerId(), invoice.getBuildingId(),
                        invoice.getMonth(), invoice.getYear(), invoice.getTotalAmount(), invoice.getDueDate());
                invoice.setOverdueNotified(true);
                invoices.save(invoice);
            }
            roomInvoices.recomputeForShare(invoice);
            count++;
        }
        return count;
    }
}
