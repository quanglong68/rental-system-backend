package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.entity.Invoice;
import com.rental.billingutilityservice.entity.RoomInvoice;
import com.rental.billingutilityservice.enums.InvoiceItemType;
import com.rental.billingutilityservice.enums.InvoiceStatus;
import com.rental.billingutilityservice.repository.InvoiceItemRepository;
import com.rental.billingutilityservice.repository.InvoiceRepository;
import com.rental.billingutilityservice.repository.RoomInvoiceRepository;
import com.rental.billingutilityservice.support.AuthSupport;
import com.rental.common.security.HeaderUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Hoa don tong phong (docs muc 11.4): tong + trang thai tinh lai tu cac share. */
@ExtendWith(MockitoExtension.class)
class RoomInvoiceServiceTest {

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

    private RoomInvoice master() {
        RoomInvoice master = new RoomInvoice();
        master.setId(50L);
        master.setRoomId(10L);
        master.setBuildingId(7L);
        master.setMonth(9);
        master.setYear(2026);
        master.setTotalAmount(BigDecimal.ZERO);
        master.setPaidAmount(BigDecimal.ZERO);
        master.setStatus(InvoiceStatus.UNPAID);
        master.setIssuedAt(Instant.parse("2026-10-01T00:00:00Z"));
        master.setDueDate(LocalDate.of(2026, 10, 8));
        return master;
    }

    private Invoice share(long id, String total, String paid, InvoiceStatus status) {
        Invoice invoice = new Invoice();
        invoice.setId(id);
        invoice.setRoomInvoiceId(50L);
        invoice.setContractTenantId(id + 100);
        invoice.setCustomerId(id + 1000);
        invoice.setTotalAmount(new BigDecimal(total));
        invoice.setPaidAmount(new BigDecimal(paid));
        invoice.setStatus(status);
        invoice.setIssuedAt(Instant.parse("2026-10-01T00:00:00Z"));
        invoice.setDueDate(LocalDate.of(2026, 10, 8));
        return invoice;
    }

    @Test
    void aggregatesTotalsAndPartialStatus() {
        RoomInvoice master = master();
        when(invoices.findByRoomInvoiceId(50L)).thenReturn(List.of(
                share(1L, "2500000", "1000000", InvoiceStatus.PARTIALLY_PAID),
                share(2L, "500000", "0", InvoiceStatus.UNPAID)));
        when(masters.save(any())).thenAnswer(call -> call.getArgument(0));

        RoomInvoice result = service.recompute(master);

        assertEquals(new BigDecimal("3000000"), result.getTotalAmount());
        assertEquals(new BigDecimal("1000000"), result.getPaidAmount());
        assertEquals(InvoiceStatus.PARTIALLY_PAID, result.getStatus());
    }

    @Test
    void aggregatesBreakdownByItemType() {
        RoomInvoice master = master();
        when(invoices.findByRoomInvoiceId(50L)).thenReturn(List.of(
                share(1L, "2500000", "0", InvoiceStatus.UNPAID)));
        when(items.sumByRoomInvoice(50L)).thenReturn(List.of(
                line(InvoiceItemType.RENT, "2500000"),
                line(InvoiceItemType.ELECTRIC, "120000"),
                line(InvoiceItemType.WATER, "50000"),
                line(InvoiceItemType.SERVICE_SHARED, "30000"),
                line(InvoiceItemType.SERVICE_PERSONAL, "20000")));
        when(masters.save(any())).thenAnswer(call -> call.getArgument(0));

        RoomInvoice result = service.recompute(master);

        assertEquals(new BigDecimal("2500000"), result.getRentAmount());
        assertEquals(new BigDecimal("120000"), result.getElectricAmount());
        assertEquals(new BigDecimal("50000"), result.getWaterAmount());
        assertEquals(new BigDecimal("50000"), result.getServiceAmount());
    }

    private InvoiceItemRepository.ItemTypeTotal line(InvoiceItemType type, String total) {
        return new InvoiceItemRepository.ItemTypeTotal() {
            @Override
            public InvoiceItemType getItemType() {
                return type;
            }

            @Override
            public BigDecimal getTotal() {
                return new BigDecimal(total);
            }
        };
    }

    @Test
    void allSharesPaidMakesMasterPaid() {
        RoomInvoice master = master();
        when(invoices.findByRoomInvoiceId(50L)).thenReturn(List.of(
                share(1L, "2500000", "2500000", InvoiceStatus.PAID),
                share(2L, "500000", "500000", InvoiceStatus.PAID)));
        when(masters.save(any())).thenAnswer(call -> call.getArgument(0));

        RoomInvoice result = service.recompute(master);

        assertEquals(InvoiceStatus.PAID, result.getStatus());
    }

    @Test
    void overdueShareMakesMasterOverdue() {
        RoomInvoice master = master();
        when(invoices.findByRoomInvoiceId(50L)).thenReturn(List.of(
                share(1L, "2500000", "1000000", InvoiceStatus.OVERDUE),
                share(2L, "500000", "0", InvoiceStatus.UNPAID)));
        when(masters.save(any())).thenAnswer(call -> call.getArgument(0));

        RoomInvoice result = service.recompute(master);

        assertEquals(InvoiceStatus.OVERDUE, result.getStatus());
    }

    @Test
    void detail_memberWhoIsNotRepresentative_seesEveryShareAmount() {
        RoomInvoice master = master();
        Invoice mine = share(1L, "2500000", "1000000", InvoiceStatus.PARTIALLY_PAID);
        Invoice other = share(2L, "500000", "0", InvoiceStatus.UNPAID);
        other.setRepresentative(true);
        other.setCustomerId(1002L);
        when(masters.findById(50L)).thenReturn(java.util.Optional.of(master));
        when(invoices.findByRoomInvoiceId(50L)).thenReturn(List.of(mine, other));

        var response = service.detail(50L, new HeaderUser("1001", "CUSTOMER", List.of("CUSTOMER")));

        assertEquals(new BigDecimal("2500000"), response.getShares().get(0).getTotalAmount());
        assertFalse(response.getShares().get(0).isMasked());
        assertFalse(response.getShares().get(0).isRepresentative());
        assertFalse(response.getShares().get(1).isMasked());
        assertEquals(new BigDecimal("500000"), response.getShares().get(1).getTotalAmount());
        assertEquals(new BigDecimal("0"), response.getShares().get(1).getPaidAmount());
        assertEquals("UNPAID", response.getShares().get(1).getStatus());
    }

    @Test
    void detail_representative_seesEveryShareAmount() {
        RoomInvoice master = master();
        Invoice mine = share(1L, "2500000", "1000000", InvoiceStatus.PARTIALLY_PAID);
        mine.setRepresentative(true);
        Invoice other = share(2L, "500000", "0", InvoiceStatus.UNPAID);
        other.setCustomerId(1002L);
        when(masters.findById(50L)).thenReturn(java.util.Optional.of(master));
        when(invoices.findByRoomInvoiceId(50L)).thenReturn(List.of(mine, other));

        var response = service.detail(50L, new HeaderUser("1001", "CUSTOMER", List.of("CUSTOMER")));

        assertEquals(new BigDecimal("2500000"), response.getShares().get(0).getTotalAmount());
        assertTrue(response.getShares().get(0).isRepresentative());
        assertFalse(response.getShares().get(0).isMasked());
        assertEquals(new BigDecimal("500000"), response.getShares().get(1).getTotalAmount());
        assertFalse(response.getShares().get(1).isMasked());
    }

    @Test
    void detail_customerWithoutShare_isForbidden() {
        RoomInvoice master = master();
        when(masters.findById(50L)).thenReturn(java.util.Optional.of(master));
        when(invoices.findByRoomInvoiceId(50L)).thenReturn(List.of(share(1L, "2500000", "0", InvoiceStatus.UNPAID)));

        assertThrows(com.rental.common.error.BusinessException.class,
                () -> service.detail(50L, new HeaderUser("9999", "CUSTOMER", List.of("CUSTOMER"))));
    }
}
