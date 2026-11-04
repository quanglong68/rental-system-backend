package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.client.BillingDataResponse;
import com.rental.billingutilityservice.client.ContractClient;
import com.rental.billingutilityservice.dto.request.CreateReadingRequest;
import com.rental.billingutilityservice.dto.request.SaveHandoverRequest;
import com.rental.billingutilityservice.dto.request.UpdateReadingRequest;
import com.rental.billingutilityservice.dto.response.MeterReadingOverviewResponse;
import com.rental.billingutilityservice.dto.response.MeterReadingResponse;
import com.rental.common.dto.PageResponse;
import com.rental.billingutilityservice.entity.Invoice;
import com.rental.billingutilityservice.entity.Meter;
import com.rental.billingutilityservice.entity.MeterReading;
import com.rental.billingutilityservice.enums.InvoiceStatus;
import com.rental.billingutilityservice.repository.InvoiceRepository;
import com.rental.billingutilityservice.repository.MeterReadingRepository;
import com.rental.billingutilityservice.repository.MeterRepository;
import com.rental.billingutilityservice.support.AuthSupport;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Chỉ số điện nước (docs mục 9.5): KY_THUAT/QUAN_LY nhập. prevIndex từ tháng trước hoặc
 * initialIndex; consumption = current - prev (>= 0, neu khong 422 INDEX_LOWER_THAN_PREVIOUS);
 * amount = consumption x unit_price hiện hành. Mỗi đồng hồ mỗi tháng một dòng (409 READING_EXISTS).
 * Sửa được cho đến khi tháng đó đã sinh hóa đơn (422 MONTH_ALREADY_INVOICED).
 */
@Service
@RequiredArgsConstructor
public class MeterReadingService {

    private static final Logger log = LoggerFactory.getLogger(MeterReadingService.class);
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MeterReadingRepository readings;
    private final MeterRepository meters;
    private final InvoiceRepository invoices;
    private final ContractClient contractClient;
    private final InvoiceAutomationService invoiceAutomation;
    private final AuthSupport auth;

    @Transactional
    public MeterReadingResponse create(CreateReadingRequest request) {
        Meter meter = meters.findById(request.getMeterId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đồng hồ"));
        auth.requireBuildingScope(meter.getBuildingId());
        if (readings.existsByMeter_IdAndMonthAndYear(request.getMeterId(), request.getMonth(), request.getYear())) {
            throw new BusinessException("READING_EXISTS", "Đồng hồ đã có chỉ số tháng này", HttpStatus.CONFLICT);
        }
        assertInvoiceEditable(meter.getRoomId(), request.getMonth(), request.getYear());
        BigDecimal prevIndex = resolvePrevIndex(meter, request.getMonth(), request.getYear());
        if (request.getCurrentIndex().compareTo(prevIndex) < 0) {
            throw new BusinessException("INDEX_LOWER_THAN_PREVIOUS", "Chỉ số mới thấp hơn chỉ số trước",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        MeterReading reading = new MeterReading();
        reading.setMeter(meter);
        reading.setMonth(request.getMonth());
        reading.setYear(request.getYear());
        reading.setReadingDate(request.getReadingDate() == null
                ? LocalDate.now(java.time.ZoneOffset.UTC)
                : request.getReadingDate());
        reading.setPrevIndex(prevIndex);
        reading.setCurrentIndex(request.getCurrentIndex());
        reading.setConsumption(request.getCurrentIndex().subtract(prevIndex));
        reading.setUnitPrice(meter.getUtilityType().getUnitPrice());
        reading.setAmount(reading.getConsumption().multiply(reading.getUnitPrice()));
        reading.setEnteredBy(auth.currentUserId());
        MeterReading saved = readings.save(reading);
        triggerAutoInvoice(meter.getRoomId(), request.getMonth(), request.getYear());
        return toResponse(saved);
    }

    @Transactional
    public MeterReadingResponse update(Long id, UpdateReadingRequest request) {
        MeterReading reading = readings.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy chỉ số"));
        auth.requireBuildingScope(reading.getMeter().getBuildingId());
        assertInvoiceEditable(reading.getMeter().getRoomId(), reading.getMonth(), reading.getYear());
        if (request.getCurrentIndex().compareTo(reading.getPrevIndex()) < 0) {
            throw new BusinessException("INDEX_LOWER_THAN_PREVIOUS", "Chỉ số mới thấp hơn chỉ số trước",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        reading.setCurrentIndex(request.getCurrentIndex());
        reading.setConsumption(request.getCurrentIndex().subtract(reading.getPrevIndex()));
        reading.setAmount(reading.getConsumption().multiply(reading.getUnitPrice()));
        MeterReading saved = readings.save(reading);
        triggerAutoInvoice(reading.getMeter().getRoomId(), reading.getMonth(), reading.getYear());
        return toResponse(saved);
    }

    /**
     * Ghi chi so ban giao cho cac dong ho cua phong moi. Chi so nay la moc initialIndex,
     * khong phai chi so chot thang, nen khong sinh tien va khong tao hoa don.
     */
    @Transactional
    public void saveHandover(SaveHandoverRequest request) {
        LocalDate handoverDate = request.getReadingDate() == null ? LocalDate.now(VN) : request.getReadingDate();
        Map<Long, SaveHandoverRequest.MeterIndex> byId = new HashMap<>();
        for (SaveHandoverRequest.MeterIndex item : request.getReadings()) {
            byId.put(item.getMeterId(), item);
        }
        List<Meter> roomMeters = meters.findByRoomIdAndIsActiveTrue(request.getRoomId());
        if (roomMeters.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Phòng chưa có đồng hồ đang hoạt động");
        }
        for (Meter meter : roomMeters) {
            auth.requireBuildingScope(meter.getBuildingId());
            if (!byId.containsKey(meter.getId())) {
                continue;
            }
            if (readings.existsFrom(meter.getId(), handoverDate.getYear(), handoverDate.getMonthValue())) {
                throw new BusinessException("HANDOVER_ALREADY_CLOSED",
                        "Đồng hồ đã có chỉ số chốt tháng, không sửa mốc bàn giao",
                        HttpStatus.UNPROCESSABLE_ENTITY);
            }
        }
        for (Meter meter : roomMeters) {
            SaveHandoverRequest.MeterIndex item = byId.get(meter.getId());
            if (item == null) {
                continue;
            }
            if (item.getCurrentIndex().signum() < 0) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ số bàn giao không được âm");
            }
            meter.setInitialIndex(item.getCurrentIndex());
            meter.setInitialIndexDate(handoverDate);
            meters.save(meter);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<MeterReadingResponse> search(Long meterId, Long roomId, Long buildingId, Integer month,
            Integer year, Pageable pageable) {
        Page<MeterReading> page;
        if (buildingId != null) {
            auth.requireBuildingScope(buildingId);
            page = readings.search(meterId, roomId, buildingId, month, year, pageable);
        } else if (auth.isAdmin()) {
            page = readings.search(meterId, roomId, null, month, year, pageable);
        } else {
            // STAFF chi xem toa minh phu trach (docs 6.2), loc o DB de phan trang dung.
            List<Long> allowed = auth.staffBuildingIds(auth.currentUserId());
            page = allowed.isEmpty() ? Page.empty(pageable)
                    : readings.searchIn(allowed, meterId, roomId, month, year, pageable);
        }
        return PageResponse.<MeterReadingResponse>builder().content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber()).size(page.getSize()).totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages()).build();
    }

    /**
     * Tinh trang ky chot so: phong cho ban giao (khach chuyen vao trong thang hien tai) va
     * phong con thieu chi so cua ky duoc chon (thuong la thang truoc).
     */
    @Transactional(readOnly = true)
    public MeterReadingOverviewResponse overview(int month, int year, Long buildingId) {
        if (month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "month phải trong khoảng 1-12");
        }
        List<Long> allowed = null;
        if (buildingId != null) {
            auth.requireBuildingScope(buildingId);
        } else if (!auth.isAdmin()) {
            allowed = auth.staffBuildingIds(auth.currentUserId());
            if (allowed.isEmpty()) {
                return emptyOverview(month, year);
            }
        }
        YearMonth target = YearMonth.of(year, month);
        YearMonth current = YearMonth.from(LocalDate.now(VN));
        boolean contractsAvailable = true;

        List<BillingDataResponse> closingContracts = fetchContracts(month, year);
        if (closingContracts == null) {
            contractsAvailable = false;
            closingContracts = List.of();
        }
        List<BillingDataResponse> handoverContracts = target.equals(current)
                ? closingContracts
                : fetchContracts(current.getMonthValue(), current.getYear());
        if (handoverContracts == null) {
            contractsAvailable = false;
            handoverContracts = List.of();
        }

        List<MeterReadingOverviewResponse.HandoverRoom> handoverRooms =
                handoverRooms(handoverContracts, current, buildingId, allowed);
        // Chỉ số của một tháng chỉ đến hạn sau khi tháng đó kết thúc (nhập trong 5 ngày đầu tháng sau).
        // Chọn tháng hiện tại/tương lai thì chưa có phòng nào "thiếu", tránh tô đỏ oan.
        List<MeterReadingOverviewResponse.MissingRoom> missingRooms = target.isBefore(current)
                ? missingRooms(closingContracts, target, buildingId, allowed, handoverRooms)
                : List.of();

        return MeterReadingOverviewResponse.builder().month(month).year(year)
                .dayOfMonth(LocalDate.now(VN).getDayOfMonth())
                .firstFiveDays(LocalDate.now(VN).getDayOfMonth() <= 5)
                .contractsAvailable(contractsAvailable)
                .handoverRooms(handoverRooms).missingRooms(missingRooms).build();
    }

    private List<BillingDataResponse> fetchContracts(int month, int year) {
        try {
            return contractClient.getBillingData(month, year);
        } catch (RuntimeException ex) {
            // Khong de Contract loi lam trang chi so khong mo duoc; FE hien canh bao tam thoi.
            log.warn("Không lấy được hợp đồng cho kỳ {}/{}: {}", month, year, ex.toString());
            return null;
        }
    }

    private List<MeterReadingOverviewResponse.HandoverRoom> handoverRooms(List<BillingDataResponse> contracts,
            YearMonth current, Long buildingId, List<Long> allowed) {
        LocalDate today = LocalDate.now(VN);
        List<MeterReadingOverviewResponse.HandoverRoom> rooms = new ArrayList<>();
        for (BillingDataResponse contract : contracts == null ? List.<BillingDataResponse>of() : contracts) {
            if (!inScope(contract, buildingId, allowed) || contract.getStartDate() == null) {
                continue;
            }
            // Ban giao chi tinh cho khach da/den ngay chuyen vao trong thang hien tai.
            if (!YearMonth.from(contract.getStartDate()).equals(current)
                    || contract.getStartDate().isAfter(today)) {
                continue;
            }
            List<Meter> roomMeters = meters.findByRoomIdAndIsActiveTrue(contract.getRoomId());
            if (roomMeters.isEmpty() || hasReading(roomMeters, current)) {
                continue;
            }
            boolean missingBaseline = roomMeters.stream().anyMatch(m -> m.getInitialIndexDate() == null
                    || m.getInitialIndexDate().isBefore(contract.getStartDate()));
            if (!missingBaseline) {
                continue;
            }
            rooms.add(MeterReadingOverviewResponse.HandoverRoom.builder()
                    .contractId(contract.getContractId()).roomId(contract.getRoomId())
                    .buildingId(contract.getBuildingId()).roomNumber(contract.getRoomNumber())
                    .moveInDate(contract.getStartDate())
                    .meters(roomMeters.stream().map(m -> meterItem(m, current.getMonthValue(),
                            current.getYear())).toList())
                    .build());
        }
        return rooms;
    }

    private List<MeterReadingOverviewResponse.MissingRoom> missingRooms(List<BillingDataResponse> contracts,
            YearMonth target, Long buildingId, List<Long> allowed,
            List<MeterReadingOverviewResponse.HandoverRoom> handoverRooms) {
        List<MeterReadingOverviewResponse.MissingRoom> rooms = new ArrayList<>();
        for (BillingDataResponse contract : contracts == null ? List.<BillingDataResponse>of() : contracts) {
            if (!inScope(contract, buildingId, allowed) || contract.getStartDate() == null
                    || contract.getEndDate() == null) {
                continue;
            }
            if (contract.getStartDate().isAfter(target.atEndOfMonth())
                    || contract.getEndDate().isBefore(target.atDay(1))) {
                continue;
            }
            // Phong dang cho ban giao duoc hien o tab rieng, khong ep chot so thang.
            boolean pendingHandover = handoverRooms.stream().anyMatch(h -> h.getRoomId().equals(contract.getRoomId()));
            if (pendingHandover) {
                continue;
            }
            List<Meter> roomMeters = meters.findByRoomIdAndIsActiveTrue(contract.getRoomId());
            List<MeterReadingOverviewResponse.MeterItem> missing = roomMeters.stream()
                    .filter(m -> readings.findByMeter_IdAndMonthAndYear(m.getId(), target.getMonthValue(),
                            target.getYear()).isEmpty())
                    .map(m -> meterItem(m, target.getMonthValue(), target.getYear())).toList();
            if (!missing.isEmpty()) {
                rooms.add(MeterReadingOverviewResponse.MissingRoom.builder()
                        .roomId(contract.getRoomId()).buildingId(contract.getBuildingId())
                        .roomNumber(contract.getRoomNumber()).meters(missing).build());
            }
        }
        return rooms;
    }

    private static boolean inScope(BillingDataResponse contract, Long buildingId, List<Long> allowed) {
        if (buildingId != null && !buildingId.equals(contract.getBuildingId())) {
            return false;
        }
        return allowed == null || allowed.contains(contract.getBuildingId());
    }

    private boolean hasReading(List<Meter> roomMeters, YearMonth month) {
        return roomMeters.stream().anyMatch(m -> readings
                .findByMeter_IdAndMonthAndYear(m.getId(), month.getMonthValue(), month.getYear()).isPresent());
    }

    private MeterReadingOverviewResponse.MeterItem meterItem(Meter meter, int month, int year) {
        BigDecimal current = readings.findByMeter_IdAndMonthAndYear(meter.getId(), month, year)
                .map(MeterReading::getCurrentIndex).orElse(null);
        return MeterReadingOverviewResponse.MeterItem.builder().meterId(meter.getId())
                .utilityTypeName(meter.getUtilityType() == null ? null : meter.getUtilityType().getName())
                .initialIndex(meter.getInitialIndex()).initialIndexDate(meter.getInitialIndexDate())
                .currentIndex(current).build();
    }

    private MeterReadingOverviewResponse emptyOverview(int month, int year) {
        return MeterReadingOverviewResponse.builder().month(month).year(year).dayOfMonth(0)
                .firstFiveDays(false).contractsAvailable(true).handoverRooms(List.of())
                .missingRooms(List.of()).build();
    }

    private BigDecimal resolvePrevIndex(Meter meter, int month, int year) {
        int prevMonth = month == 1 ? 12 : month - 1;
        int prevYear = month == 1 ? year - 1 : year;
        Optional<MeterReading> previous = readings.findByMeter_IdAndMonthAndYear(meter.getId(), prevMonth, prevYear);
        if (previous.isPresent()) {
            return previous.get().getCurrentIndex();
        }
        // Tim chi so gan nhat truoc do (phong truong hop bo trong vai thang).
        Optional<MeterReading> latest = readings
                .findTopByMeter_IdAndYearAndMonthLessThanOrderByYearDescMonthDesc(meter.getId(), year, month);
        return latest.map(MeterReading::getCurrentIndex).orElse(meter.getInitialIndex());
    }

    /**
     * Hóa đơn tự sinh nên tháng đã có hóa đơn vẫn phải sửa được chỉ số. Chỉ khóa khi
     * tiền đã được thu (PAID/PARTIALLY_PAID hoặc có paidAmount) để không làm lệch sổ.
     */
    private void assertInvoiceEditable(Long roomId, int month, int year) {
        List<Invoice> existing = invoices.findByRoomIdAndMonthAndYear(roomId, month, year);
        boolean locked = existing.stream().anyMatch(i -> i.getStatus() == InvoiceStatus.PAID
                || i.getStatus() == InvoiceStatus.PARTIALLY_PAID
                || (i.getPaidAmount() != null && i.getPaidAmount().signum() > 0));
        if (locked) {
            throw new BusinessException("MONTH_ALREADY_INVOICED",
                    "Tháng này đã ghi nhận thanh toán, không sửa chỉ số",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /**
     * Sinh/cập nhật hóa đơn sau khi chỉ số đã commit. Chạy sau commit để transaction mới
     * của InvoiceAutomationService đọc được chỉ số vừa lưu.
     */
    private void triggerAutoInvoice(Long roomId, int month, int year) {
        Runnable task = () -> {
            try {
                invoiceAutomation.autoGenerateForRoom(roomId, month, year);
            } catch (RuntimeException ex) {
                log.warn("Tự động sinh hóa đơn phòng {} kỳ {}/{} thất bại: {}", roomId, month, year, ex.toString());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private MeterReadingResponse toResponse(MeterReading reading) {
        return MeterReadingResponse.builder().id(reading.getId()).meterId(reading.getMeter().getId())
                .roomId(reading.getMeter().getRoomId()).month(reading.getMonth()).year(reading.getYear())
                .readingDate(reading.getReadingDate()).prevIndex(reading.getPrevIndex())
                .currentIndex(reading.getCurrentIndex()).consumption(reading.getConsumption())
                .unitPrice(reading.getUnitPrice()).amount(reading.getAmount()).build();
    }
}
