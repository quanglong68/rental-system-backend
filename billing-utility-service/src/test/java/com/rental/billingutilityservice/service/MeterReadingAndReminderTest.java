package com.rental.billingutilityservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rental.billingutilityservice.client.BillingDataResponse;
import com.rental.billingutilityservice.client.ContractClient;
import com.rental.billingutilityservice.dto.request.CreateReadingRequest;
import com.rental.billingutilityservice.dto.request.SaveHandoverRequest;
import com.rental.billingutilityservice.dto.request.UpdateReadingRequest;
import com.rental.billingutilityservice.dto.response.MeterReadingOverviewResponse;
import com.rental.billingutilityservice.entity.Invoice;
import com.rental.billingutilityservice.entity.Meter;
import com.rental.billingutilityservice.entity.MeterHandover;
import com.rental.billingutilityservice.entity.MeterReading;
import com.rental.billingutilityservice.entity.UtilityType;
import com.rental.billingutilityservice.enums.InvoiceStatus;
import com.rental.billingutilityservice.outbox.BillingEventPublisher;
import com.rental.billingutilityservice.repository.ContractInfoRepository;
import com.rental.billingutilityservice.repository.InvoiceItemRepository;
import com.rental.billingutilityservice.repository.InvoiceRepository;
import com.rental.billingutilityservice.repository.MeterHandoverRepository;
import com.rental.billingutilityservice.repository.MeterReadingRepository;
import com.rental.billingutilityservice.repository.MeterRepository;
import com.rental.billingutilityservice.repository.OutboxEventRepository;
import com.rental.billingutilityservice.support.AuthSupport;
import com.rental.common.error.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeterReadingAndReminderTest {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock private MeterReadingRepository readings;
    @Mock private MeterRepository meters;
    @Mock private MeterHandoverRepository meterHandovers;
    @Mock private ContractInfoRepository contractInfos;
    @Mock private InvoiceRepository invoices;
    @Mock private ContractClient contractClient;
    @Mock private InvoiceAutomationService invoiceAutomation;
    @Mock private MeterProvisioningService meterProvisioning;
    @Mock private AuthSupport auth;

    @Mock private InvoiceGenerator generator;
    @Mock private InvoiceItemRepository items;
    @Mock private RoomInvoiceService roomInvoices;
    @Mock private BillingEventPublisher events;
    @Mock private OutboxEventRepository outbox;

    private MeterReadingService readingService;
    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        readingService = new MeterReadingService(
                readings, meters, meterHandovers, contractInfos, invoices,
                contractClient, invoiceAutomation, meterProvisioning, auth);
        invoiceService = new InvoiceService(
                contractClient, generator, invoices, items, roomInvoices,
                events, meters, readings, meterHandovers, outbox, auth);
        lenient().when(auth.currentUserId()).thenReturn(21L);
        lenient().when(auth.isAdmin()).thenReturn(true);
    }

    private static UtilityType type(long id, String name, String unit, String price) {
        UtilityType ut = new UtilityType();
        ut.setId(id);
        ut.setName(name);
        ut.setUnit(unit);
        ut.setUnitPrice(new BigDecimal(price));
        return ut;
    }

    private static Meter meter(long id, long roomId, long buildingId, UtilityType ut, String init) {
        Meter m = new Meter();
        m.setId(id);
        m.setRoomId(roomId);
        m.setBuildingId(buildingId);
        m.setUtilityType(ut);
        m.setInitialIndex(new BigDecimal(init));
        m.setActive(true);
        return m;
    }

    private static BillingDataResponse contract(long id, long roomId, long buildingId, String roomNum,
            LocalDate startDate, LocalDate endDate) {
        BillingDataResponse c = new BillingDataResponse();
        c.setContractId(id);
        c.setRoomId(roomId);
        c.setBuildingId(buildingId);
        c.setRoomNumber(roomNum);
        c.setStatus("ACTIVE");
        c.setStartDate(startDate);
        c.setEndDate(endDate);
        c.setRentAmount(new BigDecimal("5000000"));
        c.setTenants(List.of());
        return c;
    }

    @Test
    void create_futurePeriod_throws422FuturePeriodNotAllowed() {
        YearMonth future = YearMonth.now(VN).plusMonths(1);
        Meter elec = meter(11L, 105L, 2L, type(1L, "Điện", "kWh", "3500"), "150");
        when(meters.findById(11L)).thenReturn(Optional.of(elec));
        CreateReadingRequest req = new CreateReadingRequest();
        req.setMeterId(11L);
        req.setMonth(future.getMonthValue());
        req.setYear(future.getYear());
        req.setCurrentIndex(new BigDecimal("200"));

        assertThatThrownBy(() -> readingService.create(req))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("FUTURE_PERIOD_NOT_ALLOWED"));
    }

    @Test
    void create_currentMonth_isAllowedAnytime() {
        YearMonth current = YearMonth.now(VN);
        YearMonth prev = current.minusMonths(1);
        Meter elec = meter(11L, 105L, 2L, type(1L, "Điện", "kWh", "3500"), "150");
        when(meters.findById(11L)).thenReturn(Optional.of(elec));
        when(readings.existsByMeter_IdAndMonthAndYear(11L, current.getMonthValue(), current.getYear()))
                .thenReturn(false);
        when(invoices.findByRoomIdAndMonthAndYear(105L, current.getMonthValue(), current.getYear()))
                .thenReturn(List.of());
        when(readings.findByMeter_IdAndMonthAndYear(11L, prev.getMonthValue(), prev.getYear()))
                .thenReturn(Optional.empty());
        when(readings.findTopByMeter_IdAndYearAndMonthLessThanOrderByYearDescMonthDesc(
                11L, current.getYear(), current.getMonthValue())).thenReturn(Optional.empty());
        when(readings.save(any(MeterReading.class))).thenAnswer(inv -> {
            MeterReading r = inv.getArgument(0);
            r.setId(99L);
            return r;
        });

        CreateReadingRequest req = new CreateReadingRequest();
        req.setMeterId(11L);
        req.setMonth(current.getMonthValue());
        req.setYear(current.getYear());
        req.setCurrentIndex(new BigDecimal("180"));

        var saved = readingService.create(req);

        assertThat(saved.getId()).isEqualTo(99L);
        assertThat(saved.getPrevIndex()).isEqualByComparingTo("150");
        assertThat(saved.getCurrentIndex()).isEqualByComparingTo("180");
        assertThat(saved.getConsumption()).isEqualByComparingTo("30");
    }

    @Test
    void saveHandover_beforeStartDate_recordsCompletionAndOverviewKeepsCompletedRow() {
        YearMonth current = YearMonth.now(VN);
        LocalDate tomorrow = LocalDate.now(VN).plusDays(1);
        BillingDataResponse newContract = contract(9L, 105L, 2L, "B205", tomorrow, tomorrow.plusYears(1));
        Meter elec = meter(11L, 105L, 2L, type(1L, "Điện", "kWh", "3500"), "450");
        Meter water = meter(12L, 105L, 2L, type(2L, "Nước", "m3", "12000"), "65");

        when(meters.findByRoomIdAndIsActiveTrue(105L)).thenReturn(List.of(elec, water));
        when(meterHandovers.save(any(MeterHandover.class))).thenAnswer(inv -> inv.getArgument(0));
        when(meters.save(any(Meter.class))).thenAnswer(inv -> inv.getArgument(0));

        SaveHandoverRequest.MeterIndex eIdx = new SaveHandoverRequest.MeterIndex();
        eIdx.setMeterId(11L);
        eIdx.setCurrentIndex(new BigDecimal("455"));
        SaveHandoverRequest.MeterIndex wIdx = new SaveHandoverRequest.MeterIndex();
        wIdx.setMeterId(12L);
        wIdx.setCurrentIndex(new BigDecimal("66"));
        SaveHandoverRequest handoverReq = new SaveHandoverRequest();
        handoverReq.setContractId(9L);
        handoverReq.setRoomId(105L);
        handoverReq.setReadings(List.of(eIdx, wIdx));

        readingService.saveHandover(handoverReq);

        ArgumentCaptor<MeterHandover> mhCaptor = ArgumentCaptor.forClass(MeterHandover.class);
        verify(meterHandovers).save(mhCaptor.capture());
        assertThat(mhCaptor.getValue().getContractId()).isEqualTo(9L);
        assertThat(mhCaptor.getValue().getHandoverDate()).isEqualTo(LocalDate.now(VN));
        assertThat(elec.getInitialIndex()).isEqualByComparingTo("455");
        assertThat(elec.getInitialIndexDate()).isEqualTo(LocalDate.now(VN));

        YearMonth nextMonth = current.plusMonths(1);
        MeterHandover recorded = mhCaptor.getValue();
        lenient().when(contractClient.getBillingData(current.getMonthValue(), current.getYear()))
                .thenReturn(List.of(newContract));
        lenient().when(contractClient.getBillingData(nextMonth.getMonthValue(), nextMonth.getYear()))
                .thenReturn(List.of(newContract));
        when(meterProvisioning.ensureDefaultMetersForRoom(105L, 2L)).thenReturn(List.of(elec, water));
        when(meterHandovers.findByContractIdIn(any())).thenReturn(List.of(recorded));
        when(readings.search(any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

        MeterReadingOverviewResponse overview = readingService.overview(current.getMonthValue(), current.getYear(), null);

        assertThat(overview.getHandoverRooms()).hasSize(1);
        assertThat(overview.getHandoverRooms().get(0).getContractId()).isEqualTo(9L);
        assertThat(overview.getHandoverRooms().get(0).isCompleted()).isTrue();
        assertThat(overview.getHandoverRooms().get(0).getHandoverDate()).isEqualTo(LocalDate.now(VN));
    }

    @Test
    void overview_marksPaidInvoiceAsReadOnly_andUnpaidInvoiceAsEditable() {
        YearMonth past = YearMonth.now(VN).minusMonths(1);
        BillingDataResponse paidRoom = contract(1L, 101L, 1L, "A104", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        BillingDataResponse unpaidRoom = contract(2L, 102L, 2L, "B201", LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        Meter m1 = meter(1L, 101L, 1L, type(1L, "Điện", "kWh", "3500"), "100");
        Meter m2 = meter(2L, 102L, 2L, type(1L, "Điện", "kWh", "3500"), "200");

        Invoice paidInv = new Invoice();
        paidInv.setStatus(InvoiceStatus.PAID);
        Invoice unpaidInv = new Invoice();
        unpaidInv.setStatus(InvoiceStatus.UNPAID);

        when(contractClient.getBillingData(past.getMonthValue(), past.getYear()))
                .thenReturn(List.of(paidRoom, unpaidRoom));
        when(meterProvisioning.ensureDefaultMetersForRoom(101L, 1L)).thenReturn(List.of(m1));
        when(meterProvisioning.ensureDefaultMetersForRoom(102L, 2L)).thenReturn(List.of(m2));
        when(readings.search(any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));
        when(invoices.findByRoomIdAndMonthAndYear(101L, past.getMonthValue(), past.getYear()))
                .thenReturn(List.of(paidInv));
        when(invoices.findByRoomIdAndMonthAndYear(102L, past.getMonthValue(), past.getYear()))
                .thenReturn(List.of(unpaidInv));

        MeterReadingOverviewResponse overview = readingService.overview(past.getMonthValue(), past.getYear(), null);

        assertThat(overview.getClosingRooms()).hasSize(2);
        var a104 = overview.getClosingRooms().stream().filter(r -> "A104".equals(r.getRoomNumber())).findFirst().orElseThrow();
        var b201 = overview.getClosingRooms().stream().filter(r -> "B201".equals(r.getRoomNumber())).findFirst().orElseThrow();
        assertThat(a104.getInvoicePaymentStatus()).isEqualTo("PAID");
        assertThat(a104.isEditable()).isFalse();
        assertThat(b201.getInvoicePaymentStatus()).isEqualTo("UNPAID");
        assertThat(b201.isEditable()).isTrue();
    }

    @Test
    void update_paidInvoice_rejectedWith422_unpaidInvoice_recalculates() {
        YearMonth past = YearMonth.now(VN).minusMonths(1);
        Meter elec = meter(11L, 101L, 1L, type(1L, "Điện", "kWh", "3500"), "100");
        MeterReading existing = new MeterReading();
        existing.setId(50L);
        existing.setMeter(elec);
        existing.setMonth(past.getMonthValue());
        existing.setYear(past.getYear());
        existing.setPrevIndex(new BigDecimal("100"));
        existing.setCurrentIndex(new BigDecimal("150"));
        existing.setConsumption(new BigDecimal("50"));
        existing.setUnitPrice(new BigDecimal("3500"));
        existing.setAmount(new BigDecimal("175000"));
        existing.setReadingDate(LocalDate.now(VN));

        Invoice paid = new Invoice();
        paid.setStatus(InvoiceStatus.PAID);
        when(readings.findById(50L)).thenReturn(Optional.of(existing));
        when(invoices.findByRoomIdAndMonthAndYear(101L, past.getMonthValue(), past.getYear()))
                .thenReturn(List.of(paid));

        UpdateReadingRequest updateReq = new UpdateReadingRequest();
        updateReq.setCurrentIndex(new BigDecimal("160"));
        assertThatThrownBy(() -> readingService.update(50L, updateReq))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("MONTH_ALREADY_INVOICED"));

        Invoice unpaid = new Invoice();
        unpaid.setStatus(InvoiceStatus.UNPAID);
        when(invoices.findByRoomIdAndMonthAndYear(101L, past.getMonthValue(), past.getYear()))
                .thenReturn(List.of(unpaid));
        when(readings.save(any(MeterReading.class))).thenAnswer(inv -> inv.getArgument(0));

        var updated = readingService.update(50L, updateReq);
        assertThat(updated.getCurrentIndex()).isEqualByComparingTo("160");
        assertThat(updated.getConsumption()).isEqualByComparingTo("60");
        verify(invoiceAutomation).autoGenerateForRoom(101L, past.getMonthValue(), past.getYear());
    }

    @Test
    void remindMissingReadings_midMonth_doesNothing_day28_remindsOnlyUnclosedActiveRooms() {
        assertThat(invoiceService.remindMissingReadings(LocalDate.of(2026, 11, 12))).isZero();
        verify(events, never()).publishMeterReadingReminder(any(), any(), anyInt(), anyInt(), any());

        BillingDataResponse unclosed = contract(1L, 101L, 2L, "B201", LocalDate.of(2026, 10, 1), LocalDate.of(2027, 10, 1));
        BillingDataResponse closed = contract(2L, 102L, 2L, "B203", LocalDate.of(2026, 10, 1), LocalDate.of(2027, 10, 1));
        BillingDataResponse futureRoom = contract(3L, 103L, 2L, "B205", LocalDate.of(2026, 11, 30), LocalDate.of(2027, 11, 30));

        Meter mUnclosed = meter(11L, 101L, 2L, type(1L, "Điện", "kWh", "3500"), "100");
        Meter mClosed = meter(12L, 102L, 2L, type(1L, "Điện", "kWh", "3500"), "100");
        Meter mFuture = meter(13L, 103L, 2L, type(1L, "Điện", "kWh", "3500"), "100");

        when(contractClient.getBillingData(11, 2026)).thenReturn(List.of(unclosed, closed, futureRoom));
        when(meters.findByRoomIdAndIsActiveTrue(101L)).thenReturn(List.of(mUnclosed));
        when(meters.findByRoomIdAndIsActiveTrue(102L)).thenReturn(List.of(mClosed));
        when(readings.findByMeter_IdAndMonthAndYear(11L, 11, 2026)).thenReturn(Optional.empty());
        when(readings.findByMeter_IdAndMonthAndYear(12L, 11, 2026)).thenReturn(Optional.of(new MeterReading()));

        int remindedBuildings = invoiceService.remindMissingReadings(LocalDate.of(2026, 11, 28));

        assertThat(remindedBuildings).isEqualTo(1);
        verify(events, times(1)).publishMeterReadingReminder(eq(2L), eq(null), eq(11), eq(2026), eq(List.of("B201")));
    }

    @Test
    void remindMissingReadings_firstFiveDays_remindsPreviousMonth() {
        BillingDataResponse room = contract(1L, 101L, 2L, "B201", LocalDate.of(2026, 10, 1), LocalDate.of(2027, 10, 1));
        Meter m = meter(11L, 101L, 2L, type(1L, "Điện", "kWh", "3500"), "100");

        when(contractClient.getBillingData(10, 2026)).thenReturn(List.of(room));
        when(meters.findByRoomIdAndIsActiveTrue(101L)).thenReturn(List.of(m));
        when(readings.findByMeter_IdAndMonthAndYear(11L, 10, 2026)).thenReturn(Optional.empty());

        int count = invoiceService.remindMissingReadings(LocalDate.of(2026, 11, 3));

        assertThat(count).isEqualTo(1);
        verify(events).publishMeterReadingReminder(eq(2L), eq(null), eq(10), eq(2026), eq(List.of("B201")));
    }
}
