package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.client.BillingDataResponse;
import com.rental.billingutilityservice.client.ContractClient;
import com.rental.billingutilityservice.dto.response.GenerateInvoiceResult;
import com.rental.billingutilityservice.dto.response.InvoiceItemResponse;
import com.rental.billingutilityservice.dto.response.InvoiceResponse;
import com.rental.billingutilityservice.dto.response.PageResponse;
import com.rental.billingutilityservice.entity.Invoice;
import com.rental.billingutilityservice.enums.InvoiceStatus;
import com.rental.billingutilityservice.outbox.BillingEventPublisher;
import com.rental.billingutilityservice.repository.InvoiceItemRepository;
import com.rental.billingutilityservice.repository.InvoiceRepository;
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
 * thang do, uy thac sinh tung hop dong cho {@link InvoiceGenerator} (giao dich doc lap).
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
    private final BillingEventPublisher events;
    private final AuthSupport auth;

    public GenerateInvoiceResult generate(int month, int year, Long buildingId) {
        YearMonth target = YearMonth.of(year, month);
        if (target.isAfter(YearMonth.now(ZoneOffset.UTC))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Khong chot so cho thang tuong lai");
        }
        List<BillingDataResponse> contracts = fetchBillingData(month, year);
        List<GenerateInvoiceResult.SkippedRoom> skipped = new ArrayList<>();
        int generated = 0;
        for (BillingDataResponse contract : contracts) {
            if (buildingId != null && !buildingId.equals(contract.getBuildingId())) {
                continue;
            }
            try {
                generated += generator.generateForContract(contract, month, year, skipped);
            } catch (BusinessException e) {
                log.warn("Bo qua hop dong {} phong {}: {}", contract.getContractId(), contract.getRoomId(),
                        e.getMessage());
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                // Race idempotent: 2 tien trinh cung sinh -> unique (contract_tenant_id,month,year).
                // Coi nhu da sinh, bo qua de tiep tuc phong khac (docs 9.5: chay lai khong sinh trung).
                log.warn("Trung hoa don hop dong {} (race idempotent), bo qua: {}", contract.getContractId(),
                        e.getMostSpecificCause() == null ? e.getMessage()
                                : e.getMostSpecificCause().getMessage());
            } catch (RuntimeException e) {
                log.warn("Bo qua hop dong {} phong {} do loi ngoai le: {}", contract.getContractId(),
                        contract.getRoomId(), e.toString());
            }
        }
        return GenerateInvoiceResult.builder().month(month).year(year).generatedCount(generated).skipped(skipped)
                .build();
    }

    private List<BillingDataResponse> fetchBillingData(int month, int year) {
        try {
            List<BillingDataResponse> data = contractClient.getBillingData(month, year);
            return data == null ? List.of() : data;
        } catch (FeignException e) {
            log.error("Contract khong phan hoi khi chot so {}/{}: {}", month, year, e.toString());
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "Contract khong phan hoi, thu chay lai sau");
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponse> search(Long buildingId, Long roomId, Integer month, Integer year,
            String status, Pageable pageable) {
        if (buildingId != null) {
            auth.requireBuildingScope(buildingId);
        }
        InvoiceStatus statusEnum = parseStatus(status);
        Page<Invoice> page = invoices.search(buildingId, roomId, month, year, statusEnum, pageable);
        if (buildingId == null && !auth.isAdmin()) {
            // STAFF chi thay toa minh phu trach (docs 6.2): loc sau truy van, khong dua param tu khai.
            List<Long> allowed = staffBuildingsSafe();
            List<Invoice> filtered = page.getContent().stream()
                    .filter(i -> allowed.contains(i.getBuildingId())).toList();
            return PageResponse.<InvoiceResponse>builder()
                    .content(filtered.stream().map(i -> toResponse(i, false)).toList())
                    .page(page.getNumber()).size(page.getSize()).totalElements(page.getTotalElements())
                    .totalPages(page.getTotalPages()).build();
        }
        return toPage(page, false);
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
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Khong tim thay hoa don"));
        boolean admin = isAdmin(principal);
        if (!admin && "CUSTOMER".equalsIgnoreCase(principal.getUserType())
                && !String.valueOf(invoice.getCustomerId()).equals(principal.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Khong phai hoa don cua ban");
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
        return InvoiceResponse.builder().id(invoice.getId()).contractId(invoice.getContractId())
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
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "status phai la UNPAID/PARTIALLY_PAID/PAID/OVERDUE");
        }
    }

    private List<Long> staffBuildingsSafe() {
        try {
            return auth.staffBuildingIds(auth.currentUserId());
        } catch (RuntimeException e) {
            return List.of();
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
            count++;
        }
        return count;
    }
}
