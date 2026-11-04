package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.dto.response.RoomInvoiceResponse;
import com.rental.billingutilityservice.dto.response.RoomInvoiceShareResponse;
import com.rental.billingutilityservice.dto.response.RoomInvoiceSummaryResponse;
import com.rental.billingutilityservice.entity.Invoice;
import com.rental.billingutilityservice.entity.RoomInvoice;
import com.rental.billingutilityservice.enums.InvoiceStatus;
import com.rental.billingutilityservice.repository.InvoiceItemRepository;
import com.rental.billingutilityservice.repository.InvoiceRepository;
import com.rental.billingutilityservice.repository.RoomInvoiceRepository;
import com.rental.billingutilityservice.support.AuthSupport;
import com.rental.common.dto.PageResponse;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.security.HeaderUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Hoa don tong theo phong (docs muc 9.5, 11.4): moi phong/thang co mot hoa don tong,
 * tong hop tu cac hoa don tung nguoi. Tong tien + trang thai duoc tinh lai sau khi
 * sinh hoa don, ghi nhan thanh toan hoac chuyen qua han.
 */
@Service
@RequiredArgsConstructor
public class RoomInvoiceService {

    private final RoomInvoiceRepository masters;
    private final InvoiceRepository invoices;
    private final InvoiceItemRepository items;
    private final AuthSupport auth;

    /** Lay hoac tao hoa don tong cua phong/thang (unique room_id, month, year). */
    @Transactional
    public RoomInvoice getOrCreate(Long roomId, Long buildingId, int month, int year, LocalDate dueDate) {
        return masters.findByRoomIdAndMonthAndYear(roomId, month, year).orElseGet(() -> {
            RoomInvoice master = new RoomInvoice();
            master.setRoomId(roomId);
            master.setBuildingId(buildingId);
            master.setMonth(month);
            master.setYear(year);
            master.setTotalAmount(BigDecimal.ZERO);
            master.setPaidAmount(BigDecimal.ZERO);
            master.setStatus(InvoiceStatus.UNPAID);
            master.setIssuedAt(Instant.now());
            master.setDueDate(dueDate);
            return masters.save(master);
        });
    }

    /** Tinh lai tong tien, so da tra va trang thai hoa don tong tu cac share hien co. */
    @Transactional
    public RoomInvoice recompute(RoomInvoice master) {
        List<Invoice> shares = invoices.findByRoomInvoiceId(master.getId());
        BigDecimal total = shares.stream().map(Invoice::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = shares.stream().map(Invoice::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        master.setTotalAmount(total);
        master.setPaidAmount(paid);
        master.setStatus(aggregateStatus(shares, total, paid));
        applyBreakdown(master);
        if (!shares.isEmpty()) {
            master.setIssuedAt(shares.stream().map(Invoice::getIssuedAt).min(Instant::compareTo)
                    .orElse(master.getIssuedAt()));
            master.setDueDate(shares.stream().map(Invoice::getDueDate).min(LocalDate::compareTo)
                    .orElse(master.getDueDate()));
        }
        return masters.save(master);
    }

    /** Cap nhat hoa don tong cua mot share (bo qua hoa don cu chua duoc gan tong). */
    @Transactional
    public void recomputeForShare(Invoice invoice) {
        if (invoice.getRoomInvoiceId() == null) {
            return;
        }
        masters.findById(invoice.getRoomInvoiceId()).ifPresent(this::recompute);
    }

    private void applyBreakdown(RoomInvoice master) {
        BigDecimal rent = BigDecimal.ZERO;
        BigDecimal electric = BigDecimal.ZERO;
        BigDecimal water = BigDecimal.ZERO;
        BigDecimal service = BigDecimal.ZERO;
        for (InvoiceItemRepository.ItemTypeTotal line : items.sumByRoomInvoice(master.getId())) {
            BigDecimal amount = line.getTotal() == null ? BigDecimal.ZERO : line.getTotal();
            switch (line.getItemType()) {
                case RENT -> rent = rent.add(amount);
                case ELECTRIC -> electric = electric.add(amount);
                case WATER -> water = water.add(amount);
                case SERVICE_SHARED, SERVICE_PERSONAL -> service = service.add(amount);
            }
        }
        master.setRentAmount(rent);
        master.setElectricAmount(electric);
        master.setWaterAmount(water);
        master.setServiceAmount(service);
    }

    private InvoiceStatus aggregateStatus(List<Invoice> shares, BigDecimal total, BigDecimal paid) {
        if (shares.isEmpty()) {
            return InvoiceStatus.UNPAID;
        }
        if (total.signum() > 0 && paid.compareTo(total) >= 0) {
            return InvoiceStatus.PAID;
        }
        if (shares.stream().anyMatch(s -> s.getStatus() == InvoiceStatus.OVERDUE)) {
            return InvoiceStatus.OVERDUE;
        }
        if (paid.signum() > 0) {
            return InvoiceStatus.PARTIALLY_PAID;
        }
        return InvoiceStatus.UNPAID;
    }

    /** Tong hop thang/nam cho trang Tong quan: KPI, chuoi 12 thang, theo toa nha, top cong no. */
    @Transactional(readOnly = true)
    public RoomInvoiceSummaryResponse summary(int month, int year) {
        if (month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "month phải trong khoảng 1..12");
        }
        if (year < 2000 || year > 2100) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "year phải trong khoảng 2000..2100");
        }
        List<RoomInvoice> invoices = masters.findByYear(year);
        List<RoomInvoice> monthInvoices = invoices.stream()
                .filter(invoice -> Integer.valueOf(month).equals(invoice.getMonth()))
                .toList();
        return RoomInvoiceSummaryResponse.builder()
                .year(year)
                .month(month)
                .monthSummary(summarize(monthInvoices))
                .yearSummary(summarize(invoices))
                .monthly(monthlyPoints(invoices))
                .byBuilding(buildingSummaries(monthInvoices))
                .topDebtRooms(topDebtRooms(monthInvoices))
                .build();
    }

    private RoomInvoiceSummaryResponse.PeriodSummary summarize(List<RoomInvoice> invoices) {
        BigDecimal billed = total(invoices, RoomInvoice::getTotalAmount);
        BigDecimal paid = total(invoices, RoomInvoice::getPaidAmount);
        return RoomInvoiceSummaryResponse.PeriodSummary.builder()
                .billed(billed)
                .paid(paid)
                .debt(billed.subtract(paid).max(BigDecimal.ZERO))
                .invoiceCount(invoices.size())
                .paidCount((int) invoices.stream().filter(i -> i.getStatus() == InvoiceStatus.PAID).count())
                .overdueCount((int) invoices.stream().filter(i -> i.getStatus() == InvoiceStatus.OVERDUE).count())
                .rent(total(invoices, RoomInvoice::getRentAmount))
                .electric(total(invoices, RoomInvoice::getElectricAmount))
                .water(total(invoices, RoomInvoice::getWaterAmount))
                .service(total(invoices, RoomInvoice::getServiceAmount))
                .build();
    }

    private BigDecimal total(List<RoomInvoice> invoices,
            java.util.function.Function<RoomInvoice, BigDecimal> getter) {
        return invoices.stream().map(getter).filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<RoomInvoiceSummaryResponse.MonthlyPoint> monthlyPoints(List<RoomInvoice> invoices) {
        Map<Integer, List<RoomInvoice>> byMonth = invoices.stream()
                .collect(Collectors.groupingBy(RoomInvoice::getMonth));
        return IntStream.rangeClosed(1, 12).mapToObj(month -> {
            List<RoomInvoice> ofMonth = byMonth.getOrDefault(month, List.of());
            return RoomInvoiceSummaryResponse.MonthlyPoint.builder()
                    .month(month)
                    .billed(total(ofMonth, RoomInvoice::getTotalAmount))
                    .paid(total(ofMonth, RoomInvoice::getPaidAmount))
                    .build();
        }).toList();
    }

    private List<RoomInvoiceSummaryResponse.BuildingSummary> buildingSummaries(List<RoomInvoice> invoices) {
        return invoices.stream()
                .collect(Collectors.groupingBy(RoomInvoice::getBuildingId))
                .entrySet().stream()
                .map(entry -> {
                    RoomInvoiceSummaryResponse.PeriodSummary totals = summarize(entry.getValue());
                    return RoomInvoiceSummaryResponse.BuildingSummary.builder()
                            .buildingId(entry.getKey())
                            .billed(totals.getBilled())
                            .paid(totals.getPaid())
                            .debt(totals.getDebt())
                            .invoiceCount(totals.getInvoiceCount())
                            .overdueCount(totals.getOverdueCount())
                            .build();
                })
                .sorted(Comparator.comparing(RoomInvoiceSummaryResponse.BuildingSummary::getDebt).reversed())
                .toList();
    }

    private List<RoomInvoiceSummaryResponse.RoomDebt> topDebtRooms(List<RoomInvoice> invoices) {
        return invoices.stream()
                .map(invoice -> Map.entry(invoice, outstanding(invoice)))
                .filter(entry -> entry.getValue().signum() > 0)
                .sorted((left, right) -> right.getValue().compareTo(left.getValue()))
                .limit(5)
                .map(entry -> RoomInvoiceSummaryResponse.RoomDebt.builder()
                        .roomInvoiceId(entry.getKey().getId())
                        .roomId(entry.getKey().getRoomId())
                        .buildingId(entry.getKey().getBuildingId())
                        .outstanding(entry.getValue())
                        .dueDate(entry.getKey().getDueDate())
                        .status(entry.getKey().getStatus().name())
                        .build())
                .toList();
    }

    private BigDecimal outstanding(RoomInvoice invoice) {
        BigDecimal billed = invoice.getTotalAmount() == null ? BigDecimal.ZERO : invoice.getTotalAmount();
        BigDecimal paid = invoice.getPaidAmount() == null ? BigDecimal.ZERO : invoice.getPaidAmount();
        return billed.subtract(paid).max(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public PageResponse<RoomInvoiceResponse> search(Long buildingId, Integer month, Integer year, String status,
            Pageable pageable) {
        InvoiceStatus statusEnum = parseStatus(status);
        if (buildingId != null) {
            auth.requireBuildingScope(buildingId);
            return toPage(masters.search(buildingId, month, year, statusEnum, pageable));
        }
        if (auth.isAdmin()) {
            return toPage(masters.search(null, month, year, statusEnum, pageable));
        }
        List<Long> allowed = auth.staffBuildingIds(auth.currentUserId());
        if (allowed.isEmpty()) {
            return PageResponse.<RoomInvoiceResponse>builder().content(List.of()).page(pageable.getPageNumber())
                    .size(pageable.getPageSize()).totalElements(0).totalPages(0).build();
        }
        return toPage(masters.searchIn(allowed, month, year, statusEnum, pageable));
    }

    @Transactional(readOnly = true)
    public PageResponse<RoomInvoiceResponse> mine(Long customerId, Integer month, Integer year, String status,
            Pageable pageable) {
        InvoiceStatus statusEnum = parseStatus(status);
        return toPage(masters.searchMine(customerId, month, year, statusEnum, pageable));
    }

    /**
     * Khach chi thay so tien cua chinh minh; nguoi dai dien phong duoc thay ca phan cua
     * cac thanh vien khac (docs/design/rental-contract-redesign.md).
     */
    @Transactional(readOnly = true)
    public RoomInvoiceResponse detail(Long id, HeaderUser principal) {
        RoomInvoice master = masters.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy hóa đơn tổng"));
        List<Invoice> shares = invoices.findByRoomInvoiceId(id);
        boolean admin = "ADMIN".equalsIgnoreCase(principal.getUserType())
                || principal.getRoles().contains("ADMIN");
        boolean customer = "CUSTOMER".equalsIgnoreCase(principal.getUserType());
        Long maskOthersFor = null;
        if (!admin) {
            if (customer) {
                String viewerId = principal.getUserId();
                List<Invoice> own = shares.stream()
                        .filter(s -> String.valueOf(s.getCustomerId()).equals(viewerId))
                        .toList();
                if (own.isEmpty()) {
                    throw new BusinessException(ErrorCode.FORBIDDEN, "Không phải hóa đơn của bạn");
                }
                if (own.stream().noneMatch(Invoice::isRepresentative)) {
                    maskOthersFor = Long.valueOf(viewerId);
                }
            } else {
                auth.requireBuildingScope(master.getBuildingId());
            }
        }
        return toResponse(master, shares, maskOthersFor);
    }

    private PageResponse<RoomInvoiceResponse> toPage(Page<RoomInvoice> page) {
        return PageResponse.<RoomInvoiceResponse>builder()
                .content(page.getContent().stream().map(r -> {
                    List<Invoice> shares = invoices.findByRoomInvoiceId(r.getId());
                    return toResponse(r, shares, null);
                }).toList())
                .page(page.getNumber()).size(page.getSize()).totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages()).build();
    }

    private RoomInvoiceResponse toResponse(RoomInvoice master, List<Invoice> shares, Long maskOthersFor) {
        List<RoomInvoiceShareResponse> shareResponses = shares.stream()
                .map(s -> RoomInvoiceShareResponse.builder().invoiceId(s.getId())
                        .contractTenantId(s.getContractTenantId()).customerId(s.getCustomerId())
                        .totalAmount(s.getTotalAmount())
                        .paidAmount(s.getPaidAmount())
                        .status(s.getStatus().name()).dueDate(s.getDueDate())
                        .representative(s.isRepresentative()).masked(false).build())
                .toList();
        return RoomInvoiceResponse.builder().id(master.getId()).roomId(master.getRoomId())
                .buildingId(master.getBuildingId()).month(master.getMonth()).year(master.getYear())
                .totalAmount(master.getTotalAmount()).paidAmount(master.getPaidAmount())
                .rentAmount(master.getRentAmount()).electricAmount(master.getElectricAmount())
                .waterAmount(master.getWaterAmount()).serviceAmount(master.getServiceAmount())
                .status(master.getStatus().name()).issuedAt(master.getIssuedAt()).dueDate(master.getDueDate())
                .shareCount(shareResponses.size()).shares(shareResponses).build();
    }

    private InvoiceStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return InvoiceStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "status phải là UNPAID/PARTIALLY_PAID/PAID/OVERDUE");
        }
    }
}
