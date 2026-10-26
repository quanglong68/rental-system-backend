package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.dto.request.CreateReadingRequest;
import com.rental.billingutilityservice.dto.request.UpdateReadingRequest;
import com.rental.billingutilityservice.dto.response.MeterReadingResponse;
import com.rental.billingutilityservice.dto.response.PageResponse;
import com.rental.billingutilityservice.entity.Meter;
import com.rental.billingutilityservice.entity.MeterReading;
import com.rental.billingutilityservice.repository.InvoiceRepository;
import com.rental.billingutilityservice.repository.MeterReadingRepository;
import com.rental.billingutilityservice.repository.MeterRepository;
import com.rental.billingutilityservice.support.AuthSupport;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Chi so dien nuoc (docs muc 9.5): KY_THUAT/QUAN_LY nhap. prevIndex tu thang truoc hoac
 * initialIndex; consumption = current - prev (>= 0, neu khong 422 INDEX_LOWER_THAN_PREVIOUS);
 * amount = consumption x unit_price hien hanh. Moi dong ho moi thang mot dong (409 READING_EXISTS).
 * Sua duoc cho den khi thang do da sinh hoa don (422 MONTH_ALREADY_INVOICED).
 */
@Service
@RequiredArgsConstructor
public class MeterReadingService {

    private final MeterReadingRepository readings;
    private final MeterRepository meters;
    private final InvoiceRepository invoices;
    private final AuthSupport auth;

    @Transactional
    public MeterReadingResponse create(CreateReadingRequest request) {
        Meter meter = meters.findById(request.getMeterId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Khong tim thay dong ho"));
        auth.requireBuildingScope(meter.getBuildingId());
        if (readings.existsByMeter_IdAndMonthAndYear(request.getMeterId(), request.getMonth(), request.getYear())) {
            throw new BusinessException("READING_EXISTS", "Dong ho da co chi so thang nay", HttpStatus.CONFLICT);
        }
        assertNotInvoiced(meter.getRoomId(), request.getMonth(), request.getYear());
        BigDecimal prevIndex = resolvePrevIndex(meter, request.getMonth(), request.getYear());
        if (request.getCurrentIndex().compareTo(prevIndex) < 0) {
            throw new BusinessException("INDEX_LOWER_THAN_PREVIOUS", "Chi so moi thap hon chi so truoc",
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
        return toResponse(readings.save(reading));
    }

    @Transactional
    public MeterReadingResponse update(Long id, UpdateReadingRequest request) {
        MeterReading reading = readings.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Khong tim thay chi so"));
        auth.requireBuildingScope(reading.getMeter().getBuildingId());
        assertNotInvoiced(reading.getMeter().getRoomId(), reading.getMonth(), reading.getYear());
        if (request.getCurrentIndex().compareTo(reading.getPrevIndex()) < 0) {
            throw new BusinessException("INDEX_LOWER_THAN_PREVIOUS", "Chi so moi thap hon chi so truoc",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        reading.setCurrentIndex(request.getCurrentIndex());
        reading.setConsumption(request.getCurrentIndex().subtract(reading.getPrevIndex()));
        reading.setAmount(reading.getConsumption().multiply(reading.getUnitPrice()));
        return toResponse(readings.save(reading));
    }

    @Transactional(readOnly = true)
    public PageResponse<MeterReadingResponse> search(Long meterId, Long roomId, Long buildingId, Integer month,
            Integer year, Pageable pageable) {
        if (buildingId != null) {
            auth.requireBuildingScope(buildingId);
        }
        Page<MeterReading> page = readings.search(meterId, roomId, buildingId, month, year, pageable);
        if (buildingId == null && !auth.isAdmin()) {
            // STAFF chi xem toa minh phu trach (docs 6.2).
            java.util.List<Long> allowedIds;
            try {
                allowedIds = auth.staffBuildingIds(auth.currentUserId());
            } catch (RuntimeException e) {
                allowedIds = java.util.List.of();
            }
            final java.util.List<Long> allowed = allowedIds;
            java.util.List<MeterReadingResponse> filtered = page.getContent().stream()
                    .filter(r -> allowed.contains(r.getMeter().getBuildingId())).map(this::toResponse).toList();
            return PageResponse.<MeterReadingResponse>builder().content(filtered)
                    .page(page.getNumber()).size(page.getSize()).totalElements(page.getTotalElements())
                    .totalPages(page.getTotalPages()).build();
        }
        return PageResponse.<MeterReadingResponse>builder().content(page.getContent().stream().map(this::toResponse).toList())
                .page(page.getNumber()).size(page.getSize()).totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages()).build();
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

    private void assertNotInvoiced(Long roomId, int month, int year) {
        if (invoices.existsByRoomIdAndMonthAndYear(roomId, month, year)) {
            throw new BusinessException("MONTH_ALREADY_INVOICED", "Thang nay da sinh hoa don, khong sua chi so",
                    HttpStatus.UNPROCESSABLE_ENTITY);
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
