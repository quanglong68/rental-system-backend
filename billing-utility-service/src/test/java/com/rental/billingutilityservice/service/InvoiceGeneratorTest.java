package com.rental.billingutilityservice.service;

import com.rental.billingutilityservice.client.BillingDataResponse;
import com.rental.billingutilityservice.dto.response.GenerateInvoiceResult;
import com.rental.billingutilityservice.entity.Invoice;
import com.rental.billingutilityservice.entity.InvoiceItem;
import com.rental.billingutilityservice.entity.Meter;
import com.rental.billingutilityservice.entity.UtilityType;
import com.rental.billingutilityservice.outbox.BillingEventPublisher;
import com.rental.billingutilityservice.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Split Billing Engine (docs muc 9.5 + 11): phong 3tr, A o ca thang 9, B vao ngay 21
 * (khong dong ho, khong dich vu) -> 2 hoa don 2.5tr / 500k + InvoiceIssued.
 * Thieu chi so -> bo qua phong + MeterReadingMissing.
 */
@ExtendWith(MockitoExtension.class)
class InvoiceGeneratorTest {

    @Mock
    private MeterRepository meters;
    @Mock
    private MeterReadingRepository readings;
    @Mock
    private ContractServiceRepository contractServices;
    @Mock
    private InvoiceRepository invoices;
    @Mock
    private InvoiceItemRepository items;
    @Mock
    private BillingEventPublisher events;

    @InjectMocks
    private InvoiceGenerator generator;

    private BillingDataResponse contract() {
        BillingDataResponse contract = new BillingDataResponse();
        contract.setContractId(1L);
        contract.setRoomId(10L);
        contract.setBuildingId(7L);
        contract.setRentAmount(new BigDecimal("3000000"));
        contract.setStartDate(LocalDate.of(2026, 9, 1));
        contract.setEndDate(LocalDate.of(2026, 12, 31));
        contract.setStatus("ACTIVE");
        BillingDataResponse.TenantStay a = new BillingDataResponse.TenantStay();
        a.setContractTenantId(100L);
        a.setCustomerId(1000L);
        a.setJoinedAt(LocalDate.of(2026, 9, 1));
        BillingDataResponse.TenantStay b = new BillingDataResponse.TenantStay();
        b.setContractTenantId(101L);
        b.setCustomerId(1001L);
        b.setJoinedAt(LocalDate.of(2026, 9, 21));
        contract.setTenants(List.of(a, b));
        return contract;
    }

    @Test
    void splitsRentBetweenRoommates() {
        when(invoices.existsByContractTenantIdAndMonthAndYear(anyLong(), anyInt(), anyInt())).thenReturn(false);
        when(meters.findByRoomIdAndIsActiveTrue(10L)).thenReturn(List.of());
        when(contractServices.findByContractId(1L)).thenReturn(List.of());
        when(invoices.save(any())).thenAnswer(call -> call.getArgument(0));

        int count = generator.generateForContract(contract(), 9, 2026, new ArrayList<>());

        assertEquals(2, count);
        ArgumentCaptor<Invoice> captor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoices, org.mockito.Mockito.times(2)).save(captor.capture());
        // save goi 2 lan (moi nguoi 1 hoa don): A 2.5tr, B 500k.
        assertEquals(2, captor.getAllValues().size());
        assertEquals(new BigDecimal("2500000"), captor.getAllValues().get(0).getTotalAmount());
        assertEquals(new BigDecimal("500000"), captor.getAllValues().get(1).getTotalAmount());
        verify(events, never()).publishMeterReadingMissing(anyLong(), anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    void missingReadingSkipsRoomAndPublishesEvent() {
        Meter meter = new Meter();
        UtilityType type = new UtilityType();
        type.setName("Dien");
        type.setUnit("kWh");
        meter.setUtilityType(type);
        when(invoices.existsByContractTenantIdAndMonthAndYear(anyLong(), anyInt(), anyInt())).thenReturn(false);
        when(meters.findByRoomIdAndIsActiveTrue(10L)).thenReturn(List.of(meter));
        when(readings.findByMeter_IdAndMonthAndYear(any(), anyInt(), anyInt())).thenReturn(Optional.empty());

        List<GenerateInvoiceResult.SkippedRoom> skipped = new ArrayList<>();
        int count = generator.generateForContract(contract(), 9, 2026, skipped);

        assertEquals(0, count);
        assertEquals(1, skipped.size());
        assertEquals(10L, skipped.get(0).getRoomId());
        verify(events).publishMeterReadingMissing(anyLong(), anyLong(), anyInt(), anyInt(), any());
        verify(invoices, never()).save(any(Invoice.class));
        verify(items, never()).save(any(InvoiceItem.class));
    }

    @Test
    void rerunDoesNotDuplicate() {
        when(invoices.existsByContractTenantIdAndMonthAndYear(anyLong(), anyInt(), anyInt())).thenReturn(true);

        int count = generator.generateForContract(contract(), 9, 2026, new ArrayList<>());

        assertEquals(0, count);
        verify(invoices, never()).save(any(Invoice.class));
    }
}
