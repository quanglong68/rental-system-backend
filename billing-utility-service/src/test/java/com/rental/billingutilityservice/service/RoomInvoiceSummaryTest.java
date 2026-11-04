package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.dto.response.RoomInvoiceSummaryResponse;
import com.rental.billingutilityservice.entity.RoomInvoice;
import com.rental.billingutilityservice.enums.InvoiceStatus;
import com.rental.billingutilityservice.repository.InvoiceItemRepository;
import com.rental.billingutilityservice.repository.InvoiceRepository;
import com.rental.billingutilityservice.repository.RoomInvoiceRepository;
import com.rental.billingutilityservice.support.AuthSupport;
import com.rental.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/** Tong hop thang/nam cho trang Tong quan: KPI, chuoi 12 thang, theo toa, top cong no. */
@ExtendWith(MockitoExtension.class)
class RoomInvoiceSummaryTest {

    @Mock
    private RoomInvoiceRepository masters;
    @Mock
    private InvoiceRepository invoices;
    @Mock
    private InvoiceItemRepository items;
    @Mock
    private AuthSupport auth;

    @InjectMocks
    private RoomInvoiceService service;

    private RoomInvoice invoice(long id, long roomId, long buildingId, int month, String total, String paid,
            InvoiceStatus status, String rent) {
        RoomInvoice invoice = new RoomInvoice();
        invoice.setId(id);
        invoice.setRoomId(roomId);
        invoice.setBuildingId(buildingId);
        invoice.setMonth(month);
        invoice.setYear(2026);
        invoice.setTotalAmount(new BigDecimal(total));
        invoice.setPaidAmount(new BigDecimal(paid));
        invoice.setStatus(status);
        invoice.setDueDate(LocalDate.of(2026, month, 8));
        invoice.setIssuedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        invoice.setRentAmount(new BigDecimal(rent));
        invoice.setElectricAmount(new BigDecimal("100000"));
        invoice.setWaterAmount(new BigDecimal("50000"));
        invoice.setServiceAmount(new BigDecimal("30000"));
        return invoice;
    }

    @Test
    void summarizesSelectedMonthYearBuildingsAndDebt() {
        when(masters.findByYear(2026)).thenReturn(List.of(
                invoice(1, 101, 1, 1, "5000000", "5000000", InvoiceStatus.PAID, "4000000"),
                invoice(2, 102, 1, 2, "7000000", "2000000", InvoiceStatus.PARTIALLY_PAID, "6000000"),
                invoice(3, 201, 2, 2, "3000000", "0", InvoiceStatus.OVERDUE, "2500000"),
                invoice(4, 103, 1, 3, "4000000", "4000000", InvoiceStatus.PAID, "3500000")));

        RoomInvoiceSummaryResponse result = service.summary(2, 2026);

        RoomInvoiceSummaryResponse.PeriodSummary month = result.getMonthSummary();
        assertEquals(new BigDecimal("10000000"), month.getBilled());
        assertEquals(new BigDecimal("2000000"), month.getPaid());
        assertEquals(new BigDecimal("8000000"), month.getDebt());
        assertEquals(2, month.getInvoiceCount());
        assertEquals(0, month.getPaidCount());
        assertEquals(1, month.getOverdueCount());
        assertEquals(new BigDecimal("8500000"), month.getRent());
        assertEquals(new BigDecimal("200000"), month.getElectric());
        assertEquals(new BigDecimal("100000"), month.getWater());
        assertEquals(new BigDecimal("60000"), month.getService());

        RoomInvoiceSummaryResponse.PeriodSummary year = result.getYearSummary();
        assertEquals(new BigDecimal("19000000"), year.getBilled());
        assertEquals(new BigDecimal("11000000"), year.getPaid());
        assertEquals(new BigDecimal("8000000"), year.getDebt());
        assertEquals(4, year.getInvoiceCount());
        assertEquals(2, year.getPaidCount());

        assertEquals(12, result.getMonthly().size());
        assertEquals(1, result.getMonthly().get(0).getMonth());
        assertEquals(new BigDecimal("5000000"), result.getMonthly().get(0).getBilled());
        assertEquals(new BigDecimal("10000000"), result.getMonthly().get(1).getBilled());
        assertEquals(new BigDecimal("0"), result.getMonthly().get(3).getBilled());

        assertEquals(2, result.getByBuilding().size());
        assertEquals(1L, result.getByBuilding().get(0).getBuildingId());
        assertEquals(new BigDecimal("7000000"), result.getByBuilding().get(0).getBilled());
        assertEquals(new BigDecimal("5000000"), result.getByBuilding().get(0).getDebt());
        assertEquals(2L, result.getByBuilding().get(1).getBuildingId());

        assertEquals(2, result.getTopDebtRooms().size());
        assertEquals(102L, result.getTopDebtRooms().get(0).getRoomId());
        assertEquals(new BigDecimal("5000000"), result.getTopDebtRooms().get(0).getOutstanding());
        assertEquals("OVERDUE", result.getTopDebtRooms().get(1).getStatus());
    }

    @Test
    void topDebtRoomsKeepsFiveLargestAndSkipsPaidRooms() {
        when(masters.findByYear(2026)).thenReturn(List.of(
                invoice(1, 101, 1, 5, "1000000", "1000000", InvoiceStatus.PAID, "0"),
                invoice(2, 102, 1, 5, "2000000", "0", InvoiceStatus.UNPAID, "0"),
                invoice(3, 103, 1, 5, "3000000", "0", InvoiceStatus.UNPAID, "0"),
                invoice(4, 104, 1, 5, "4000000", "0", InvoiceStatus.UNPAID, "0"),
                invoice(5, 105, 1, 5, "5000000", "0", InvoiceStatus.UNPAID, "0"),
                invoice(6, 106, 1, 5, "6000000", "0", InvoiceStatus.UNPAID, "0"),
                invoice(7, 107, 1, 5, "7000000", "0", InvoiceStatus.UNPAID, "0")));

        RoomInvoiceSummaryResponse result = service.summary(5, 2026);

        assertEquals(5, result.getTopDebtRooms().size());
        assertEquals(107L, result.getTopDebtRooms().get(0).getRoomId());
        assertEquals(103L, result.getTopDebtRooms().get(4).getRoomId());
    }

    @Test
    void emptyYearReturnsZeroesAndTwelveMonths() {
        when(masters.findByYear(2026)).thenReturn(List.of());

        RoomInvoiceSummaryResponse result = service.summary(12, 2026);

        assertEquals(0, result.getMonthSummary().getInvoiceCount());
        assertEquals(BigDecimal.ZERO, result.getMonthSummary().getDebt());
        assertEquals(12, result.getMonthly().size());
        assertEquals(List.of(), result.getByBuilding());
        assertEquals(List.of(), result.getTopDebtRooms());
    }

    @Test
    void rejectsInvalidMonthAndYear() {
        assertThrows(BusinessException.class, () -> service.summary(0, 2026));
        assertThrows(BusinessException.class, () -> service.summary(13, 2026));
        assertThrows(BusinessException.class, () -> service.summary(10, 1999));
        assertThrows(BusinessException.class, () -> service.summary(10, 2101));
    }
}
