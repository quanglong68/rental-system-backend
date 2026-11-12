package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.client.BillingDataResponse;
import com.rental.billingutilityservice.client.ContractClient;
import com.rental.billingutilityservice.dto.request.CreateReadingRequest;
import com.rental.billingutilityservice.dto.request.SaveHandoverRequest;
import com.rental.billingutilityservice.dto.request.UpdateReadingRequest;
import com.rental.billingutilityservice.dto.response.MeterReadingOverviewResponse;
import com.rental.billingutilityservice.dto.response.MeterReadingResponse;
import com.rental.billingutilityservice.entity.ContractInfo;
import com.rental.billingutilityservice.entity.Invoice;
import com.rental.billingutilityservice.entity.Meter;
import com.rental.billingutilityservice.entity.MeterHandover;
import com.rental.billingutilityservice.entity.MeterReading;
import com.rental.billingutilityservice.enums.InvoiceStatus;
import com.rental.billingutilityservice.repository.ContractInfoRepository;
import com.rental.billingutilityservice.repository.InvoiceRepository;
import com.rental.billingutilityservice.repository.MeterHandoverRepository;
import com.rental.billingutilityservice.repository.MeterReadingRepository;
import com.rental.billingutilityservice.repository.MeterRepository;
import com.rental.billingutilityservice.support.AuthSupport;
import com.rental.common.dto.PageResponse;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chỉ số điện nước (docs mục 9.5): KY_THUAT/QUAN_LY nhập. Chặn tháng tương lai (VN);
 * cho phép nhập tháng hiện tại và sửa tháng chưa thu tiền (hóa đơn UNPAID tự tính lại).
 * Khóa sửa khi hóa đơn đã PAID / PARTIALLY_PAID.
 */
@Service
@RequiredArgsConstructor
public class MeterReadingService {

    private static final Logger log = LoggerFactory.getLogger(MeterReadingService.class);
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MeterReadingRepository readings;
    private final MeterRepository meters;
    private final MeterHandoverRepository meterHandovers;
    private final ContractInfoRepository contractInfos;
    private final InvoiceRepository invoices;
    private final ContractClient contractClient;
    private final InvoiceAutomationService invoiceAutomation;
    private final MeterProvisioningService meterProvisioning;
    private final AuthSupport auth;
    private Clock clock = Clock.system(VN);

    /** Cho phep test gia lap ngay gio Viet Nam. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Transactional
    public MeterReadingResponse create(CreateReadingRequest request) {
        Meter meter = meters.findById(request.getMeterId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy đồng hồ"));
        auth.requireBuildingScope(meter.getBuildingId());
        assertNotFuturePeriod(request.getMonth(), request.getYear());
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
                ? LocalDate.now(clock.withZone(VN))
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
        assertNotFuturePeriod(reading.getMonth(), reading.getYear());
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
        LocalDate handoverDate = request.getReadingDate() == null
                ? LocalDate.now(clock.withZone(VN))
                : request.getReadingDate();
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

        Long resolvedContractId = resolveHandoverContractId(request.getContractId(), request.getRoomId());
        if (resolvedContractId != null) {
            MeterHandover record = meterHandovers.findById(resolvedContractId).orElseGet(MeterHandover::new);
            record.setContractId(resolvedContractId);
            record.setRoomId(request.getRoomId());
            record.setBuildingId(roomMeters.get(0).getBuildingId());
            record.setHandoverDate(handoverDate);
            record.setEnteredBy(auth.currentUserId());
            meterHandovers.save(record);
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
     * Tinh trang ky chot so theo Phuong an A:
     * - Tab Ban giao khach moi: hop dong ACTIVE bat dau tu thang hien tai tro di (hoac da luu ban giao trong thang)
     *   hien thi ngay sau khi ky xong (khong doi startDate) va giu lai danh dau da nhap sau khi luu.
     * - Tab Chot chi so ky MM/YYYY: phong co hop dong hieu luc trong ky (hoac da co chi so ky do), kem trang thai
     *   thanh toan hoa don (NONE/UNPAID/PAID). Tu ngay 28 hoac thang qua khu moi danh dau do thieu chi so.
     */
    @Transactional
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
        LocalDate todayVN = LocalDate.now(clock.withZone(VN));
        YearMonth target = YearMonth.of(year, month);
        YearMonth current = YearMonth.from(todayVN);
        boolean futurePeriod = target.isAfter(current);
        boolean contractsAvailable = true;

        List<BillingDataResponse> closingContracts = futurePeriod ? List.of() : fetchContracts(month, year);
        if (closingContracts == null) {
            contractsAvailable = false;
            closingContracts = List.of();
        }
        List<BillingDataResponse> currentContracts = target.equals(current) && !futurePeriod
                ? closingContracts
                : fetchContracts(current.getMonthValue(), current.getYear());
        if (currentContracts == null) {
            contractsAvailable = false;
            currentContracts = List.of();
        }
        YearMonth nextMonth = current.plusMonths(1);
        List<BillingDataResponse> nextContracts = fetchContracts(nextMonth.getMonthValue(), nextMonth.getYear());

        List<BillingDataResponse> handoverCandidates = mergeHandoverCandidates(
                currentContracts, nextContracts == null ? List.of() : nextContracts, current);

        List<MeterReadingOverviewResponse.HandoverRoom> handoverRooms =
                handoverRooms(handoverCandidates, current, todayVN, buildingId, allowed);

        Set<Long> pendingHandoverRoomIds = handoverRooms.stream()
                .filter(h -> !h.isCompleted())
                .map(MeterReadingOverviewResponse.HandoverRoom::getRoomId)
                .collect(Collectors.toSet());

        List<MeterReadingOverviewResponse.ClosingRoom> closingRooms = new ArrayList<>();
        List<MeterReadingOverviewResponse.MissingRoom> missingRooms = new ArrayList<>();
        if (!futurePeriod) {
            buildClosingAndMissingRooms(
                    closingContracts, target, current, todayVN, buildingId, allowed,
                    pendingHandoverRoomIds, closingRooms, missingRooms);
        }

        int dayOfMonth = todayVN.getDayOfMonth();
        return MeterReadingOverviewResponse.builder()
                .month(month)
                .year(year)
                .dayOfMonth(dayOfMonth)
                .firstFiveDays(dayOfMonth <= 5)
                .endOfMonthReminder(dayOfMonth >= 28)
                .futurePeriod(futurePeriod)
                .contractsAvailable(contractsAvailable)
                .handoverRooms(handoverRooms)
                .missingRooms(missingRooms)
                .closingRooms(closingRooms)
                .build();
    }

    private List<BillingDataResponse> mergeHandoverCandidates(List<BillingDataResponse> currentContracts,
            List<BillingDataResponse> nextContracts, YearMonth current) {
        Map<Long, BillingDataResponse> byContractId = new LinkedHashMap<>();
        for (BillingDataResponse c : currentContracts) {
            if (c.getContractId() != null) {
                byContractId.putIfAbsent(c.getContractId(), c);
            }
        }
        for (BillingDataResponse c : nextContracts) {
            if (c.getContractId() != null) {
                byContractId.putIfAbsent(c.getContractId(), c);
            }
        }
        // Bo sung hop dong ACTIVE co startDate xa hon thang sau neu da dong bo qua contract_info.
        for (ContractInfo info : contractInfos.findAll()) {
            if (!"ACTIVE".equalsIgnoreCase(info.getStatus()) || info.getStartDate() == null
                    || byContractId.containsKey(info.getContractId())) {
                continue;
            }
            if (!YearMonth.from(info.getStartDate()).isBefore(current)) {
                BillingDataResponse fallback = new BillingDataResponse();
                fallback.setContractId(info.getContractId());
                fallback.setRoomId(info.getRoomId());
                fallback.setBuildingId(info.getBuildingId());
                fallback.setStatus(info.getStatus());
                fallback.setStartDate(info.getStartDate());
                fallback.setEndDate(info.getEndDate());
                byContractId.put(info.getContractId(), fallback);
            }
        }
        return new ArrayList<>(byContractId.values());
    }

    private List<BillingDataResponse> fetchContracts(int month, int year) {
        try {
            return contractClient.getBillingData(month, year);
        } catch (RuntimeException ex) {
            log.warn("Không lấy được hợp đồng cho kỳ {}/{}: {}", month, year, ex.toString());
            return null;
        }
    }

    private List<MeterReadingOverviewResponse.HandoverRoom> handoverRooms(List<BillingDataResponse> contracts,
            YearMonth current, LocalDate todayVN, Long buildingId, List<Long> allowed) {
        List<Long> contractIds = contracts.stream().map(BillingDataResponse::getContractId)
                .filter(Objects::nonNull).toList();
        Map<Long, MeterHandover> tracked = contractIds.isEmpty()
                ? Map.of()
                : meterHandovers.findByContractIdIn(contractIds).stream()
                        .collect(Collectors.toMap(MeterHandover::getContractId, h -> h, (a, b) -> a));

        List<MeterReadingOverviewResponse.HandoverRoom> rooms = new ArrayList<>();
        for (BillingDataResponse contract : contracts) {
            if (!inScope(contract, buildingId, allowed) || contract.getStartDate() == null
                    || !"ACTIVE".equalsIgnoreCase(contract.getStatus())) {
                continue;
            }
            MeterHandover mh = tracked.get(contract.getContractId());
            boolean startsCurrentOrFuture = !YearMonth.from(contract.getStartDate()).isBefore(current);
            boolean handedOverThisMonth = mh != null && YearMonth.from(mh.getHandoverDate()).equals(current);
            if (!startsCurrentOrFuture && !handedOverThisMonth) {
                continue;
            }

            List<Meter> roomMeters = meterProvisioning.ensureDefaultMetersForRoom(
                    contract.getRoomId(), contract.getBuildingId());
            if (roomMeters.isEmpty()) {
                continue;
            }

            boolean legacyDone = !contract.getStartDate().isAfter(todayVN)
                    && roomMeters.stream().allMatch(m -> m.getInitialIndexDate() != null
                            && !m.getInitialIndexDate().isBefore(contract.getStartDate()));
            boolean completed = mh != null || legacyDone;
            LocalDate handoverDate = mh != null
                    ? mh.getHandoverDate()
                    : (completed ? roomMeters.stream().map(Meter::getInitialIndexDate)
                            .filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null) : null);

            rooms.add(MeterReadingOverviewResponse.HandoverRoom.builder()
                    .contractId(contract.getContractId())
                    .roomId(contract.getRoomId())
                    .buildingId(contract.getBuildingId())
                    .roomNumber(contract.getRoomNumber())
                    .moveInDate(contract.getStartDate())
                    .completed(completed)
                    .handoverDate(handoverDate)
                    .meters(roomMeters.stream().map(m -> meterItem(m, current.getMonthValue(),
                            current.getYear())).toList())
                    .build());
        }
        rooms.sort(Comparator.comparing(MeterReadingOverviewResponse.HandoverRoom::isCompleted)
                .thenComparing(MeterReadingOverviewResponse.HandoverRoom::getMoveInDate)
                .thenComparing(MeterReadingOverviewResponse.HandoverRoom::getContractId,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        return rooms;
    }

    private void buildClosingAndMissingRooms(
            List<BillingDataResponse> contracts,
            YearMonth target,
            YearMonth current,
            LocalDate todayVN,
            Long buildingId,
            List<Long> allowed,
            Set<Long> pendingHandoverRoomIds,
            List<MeterReadingOverviewResponse.ClosingRoom> closingOut,
            List<MeterReadingOverviewResponse.MissingRoom> missingOut) {

        Map<Long, RoomCandidate> candidates = new LinkedHashMap<>();
        for (BillingDataResponse contract : contracts == null ? List.<BillingDataResponse>of() : contracts) {
            if (!inScope(contract, buildingId, allowed) || contract.getStartDate() == null
                    || contract.getEndDate() == null) {
                continue;
            }
            if (contract.getStartDate().isAfter(target.atEndOfMonth())
                    || contract.getEndDate().isBefore(target.atDay(1))) {
                continue;
            }
            if (pendingHandoverRoomIds.contains(contract.getRoomId())) {
                continue;
            }
            if (target.equals(current) && contract.getStartDate().isAfter(todayVN)) {
                continue;
            }
            RoomCandidate candidate = candidates.computeIfAbsent(contract.getRoomId(),
                    id -> new RoomCandidate(contract.getRoomId(), contract.getBuildingId(), contract.getRoomNumber()));
            if ("ACTIVE".equalsIgnoreCase(contract.getStatus())) {
                candidate.ensureMetersIfMissing = true;
            }
        }

        // Bo sung cac phong da co chi so nhap trong ky du hop dong co the da duoc lọc/thay doi.
        Page<MeterReading> periodReadings = buildingId != null
                ? readings.search(null, null, buildingId, target.getMonthValue(), target.getYear(), PageRequest.of(0, 500))
                : auth.isAdmin()
                        ? readings.search(null, null, null, target.getMonthValue(), target.getYear(), PageRequest.of(0, 500))
                        : (allowed == null || allowed.isEmpty()
                                ? Page.empty()
                                : readings.searchIn(allowed, null, null, target.getMonthValue(), target.getYear(),
                                        PageRequest.of(0, 500)));
        for (MeterReading r : periodReadings.getContent()) {
            Long roomId = r.getMeter().getRoomId();
            if (!pendingHandoverRoomIds.contains(roomId)) {
                candidates.computeIfAbsent(roomId,
                        id -> new RoomCandidate(roomId, r.getMeter().getBuildingId(), null));
            }
        }

        boolean dueNow = target.isBefore(current)
                || (target.equals(current) && todayVN.getDayOfMonth() >= 28);

        List<RoomCandidate> sorted = new ArrayList<>(candidates.values());
        sorted.sort(Comparator.comparing(
                c -> c.roomNumber != null ? c.roomNumber : String.valueOf(c.roomId),
                Comparator.naturalOrder()));

        for (RoomCandidate candidate : sorted) {
            List<Meter> roomMeters = meters.findByRoomIdAndIsActiveTrue(candidate.roomId);
            if (roomMeters.isEmpty() && candidate.ensureMetersIfMissing) {
                roomMeters = meterProvisioning.ensureDefaultMetersForRoom(candidate.roomId, candidate.buildingId);
            }
            if (roomMeters.isEmpty()) {
                continue;
            }

            List<MeterReadingOverviewResponse.MeterItem> items = roomMeters.stream()
                    .map(m -> meterItem(m, target.getMonthValue(), target.getYear()))
                    .toList();
            List<MeterReadingOverviewResponse.MeterItem> missingItems = items.stream()
                    .filter(it -> it.getCurrentIndex() == null)
                    .toList();
            boolean incomplete = !missingItems.isEmpty();
            boolean hasExistingReadings = items.stream().anyMatch(it -> it.getCurrentIndex() != null);
            boolean missingReading = incomplete && dueNow;

            List<Invoice> roomInvoices = invoices.findByRoomIdAndMonthAndYear(
                    candidate.roomId, target.getMonthValue(), target.getYear());
            boolean hasInvoice = !roomInvoices.isEmpty();
            boolean locked = isInvoiceLocked(roomInvoices);
            String paymentStatus = !hasInvoice ? "NONE" : (locked ? "PAID" : "UNPAID");

            closingOut.add(MeterReadingOverviewResponse.ClosingRoom.builder()
                    .roomId(candidate.roomId)
                    .buildingId(candidate.buildingId)
                    .roomNumber(candidate.roomNumber)
                    .missingReading(missingReading)
                    .incomplete(incomplete)
                    .hasExistingReadings(hasExistingReadings)
                    .hasInvoice(hasInvoice)
                    .editable(!locked)
                    .invoicePaymentStatus(paymentStatus)
                    .meters(items)
                    .build());

            if (missingReading) {
                missingOut.add(MeterReadingOverviewResponse.MissingRoom.builder()
                        .roomId(candidate.roomId)
                        .buildingId(candidate.buildingId)
                        .roomNumber(candidate.roomNumber)
                        .meters(missingItems)
                        .build());
            }
        }
    }

    private Long resolveHandoverContractId(Long explicitContractId, Long roomId) {
        if (explicitContractId != null) {
            return explicitContractId;
        }
        LocalDate todayVN = LocalDate.now(clock.withZone(VN));
        YearMonth current = YearMonth.from(todayVN);
        List<BillingDataResponse> combined = new ArrayList<>();
        List<BillingDataResponse> cur = fetchContracts(current.getMonthValue(), current.getYear());
        if (cur != null) combined.addAll(cur);
        YearMonth next = current.plusMonths(1);
        List<BillingDataResponse> nxt = fetchContracts(next.getMonthValue(), next.getYear());
        if (nxt != null) combined.addAll(nxt);

        return combined.stream()
                .filter(c -> Objects.equals(c.getRoomId(), roomId)
                        && "ACTIVE".equalsIgnoreCase(c.getStatus())
                        && c.getStartDate() != null
                        && !YearMonth.from(c.getStartDate()).isBefore(current))
                .map(BillingDataResponse::getContractId)
                .findFirst()
                .orElseGet(() -> contractInfos.findAll().stream()
                        .filter(ci -> Objects.equals(ci.getRoomId(), roomId)
                                && "ACTIVE".equalsIgnoreCase(ci.getStatus())
                                && ci.getStartDate() != null
                                && !YearMonth.from(ci.getStartDate()).isBefore(current))
                        .map(ContractInfo::getContractId)
                        .findFirst()
                        .orElse(null));
    }

    private static boolean inScope(BillingDataResponse contract, Long buildingId, List<Long> allowed) {
        if (buildingId != null && !buildingId.equals(contract.getBuildingId())) {
            return false;
        }
        return allowed == null || allowed.contains(contract.getBuildingId());
    }

    private MeterReadingOverviewResponse.MeterItem meterItem(Meter meter, int month, int year) {
        Optional<MeterReading> curOpt = readings.findByMeter_IdAndMonthAndYear(meter.getId(), month, year);
        int prevMonth = month == 1 ? 12 : month - 1;
        int prevYear = month == 1 ? year - 1 : year;
        Optional<MeterReading> consecutivePrev = readings.findByMeter_IdAndMonthAndYear(
                meter.getId(), prevMonth, prevYear);
        Optional<MeterReading> latestPrior = consecutivePrev.isPresent()
                ? consecutivePrev
                : readings.findTopByMeter_IdAndYearAndMonthLessThanOrderByYearDescMonthDesc(meter.getId(), year, month);

        BigDecimal prevIndex;
        String prevSource;
        if (curOpt.isPresent()) {
            prevIndex = curOpt.get().getPrevIndex();
            if (consecutivePrev.isPresent()) {
                prevSource = "kỳ trước";
            } else if (latestPrior.isPresent() && prevIndex.compareTo(meter.getInitialIndex()) != 0) {
                prevSource = "kỳ gần nhất";
            } else {
                prevSource = "bàn giao";
            }
        } else if (consecutivePrev.isPresent()) {
            prevIndex = consecutivePrev.get().getCurrentIndex();
            prevSource = "kỳ trước";
        } else if (latestPrior.isPresent()) {
            prevIndex = latestPrior.get().getCurrentIndex();
            prevSource = "kỳ gần nhất";
        } else {
            prevIndex = meter.getInitialIndex();
            prevSource = "bàn giao";
        }

        return MeterReadingOverviewResponse.MeterItem.builder()
                .meterId(meter.getId())
                .utilityTypeId(meter.getUtilityType() == null ? null : meter.getUtilityType().getId())
                .utilityTypeName(meter.getUtilityType() == null ? null : meter.getUtilityType().getName())
                .unit(meter.getUtilityType() == null ? null : meter.getUtilityType().getUnit())
                .unitPrice(curOpt.map(MeterReading::getUnitPrice)
                        .orElse(meter.getUtilityType() == null ? null : meter.getUtilityType().getUnitPrice()))
                .initialIndex(meter.getInitialIndex())
                .initialIndexDate(meter.getInitialIndexDate())
                .readingId(curOpt.map(MeterReading::getId).orElse(null))
                .prevIndex(prevIndex)
                .prevIndexSource(prevSource)
                .currentIndex(curOpt.map(MeterReading::getCurrentIndex).orElse(null))
                .build();
    }

    private MeterReadingOverviewResponse emptyOverview(int month, int year) {
        LocalDate todayVN = LocalDate.now(clock.withZone(VN));
        return MeterReadingOverviewResponse.builder()
                .month(month)
                .year(year)
                .dayOfMonth(todayVN.getDayOfMonth())
                .firstFiveDays(todayVN.getDayOfMonth() <= 5)
                .endOfMonthReminder(todayVN.getDayOfMonth() >= 28)
                .futurePeriod(YearMonth.of(year, month).isAfter(YearMonth.from(todayVN)))
                .contractsAvailable(true)
                .handoverRooms(List.of())
                .missingRooms(List.of())
                .closingRooms(List.of())
                .build();
    }

    private void assertNotFuturePeriod(int month, int year) {
        YearMonth target = YearMonth.of(year, month);
        YearMonth current = YearMonth.from(LocalDate.now(clock.withZone(VN)));
        if (target.isAfter(current)) {
            throw new BusinessException("FUTURE_PERIOD_NOT_ALLOWED",
                    "Không được nhập chỉ số cho tháng tương lai",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private BigDecimal resolvePrevIndex(Meter meter, int month, int year) {
        int prevMonth = month == 1 ? 12 : month - 1;
        int prevYear = month == 1 ? year - 1 : year;
        Optional<MeterReading> previous = readings.findByMeter_IdAndMonthAndYear(meter.getId(), prevMonth, prevYear);
        if (previous.isPresent()) {
            return previous.get().getCurrentIndex();
        }
        Optional<MeterReading> latest = readings
                .findTopByMeter_IdAndYearAndMonthLessThanOrderByYearDescMonthDesc(meter.getId(), year, month);
        return latest.map(MeterReading::getCurrentIndex).orElse(meter.getInitialIndex());
    }

    private void assertInvoiceEditable(Long roomId, int month, int year) {
        List<Invoice> existing = invoices.findByRoomIdAndMonthAndYear(roomId, month, year);
        if (isInvoiceLocked(existing)) {
            throw new BusinessException("MONTH_ALREADY_INVOICED",
                    "Tháng này đã ghi nhận thanh toán, không sửa chỉ số",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private static boolean isInvoiceLocked(List<Invoice> existing) {
        return existing.stream().anyMatch(i -> i.getStatus() == InvoiceStatus.PAID
                || i.getStatus() == InvoiceStatus.PARTIALLY_PAID
                || (i.getPaidAmount() != null && i.getPaidAmount().signum() > 0));
    }

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

    private static final class RoomCandidate {
        final Long roomId;
        final Long buildingId;
        final String roomNumber;
        boolean ensureMetersIfMissing;

        RoomCandidate(Long roomId, Long buildingId, String roomNumber) {
            this.roomId = roomId;
            this.buildingId = buildingId;
            this.roomNumber = roomNumber;
        }
    }
}
