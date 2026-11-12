package com.rental.billingutilityservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rental.billingutilityservice.entity.Meter;
import com.rental.billingutilityservice.entity.UtilityType;
import com.rental.billingutilityservice.repository.MeterRepository;
import com.rental.billingutilityservice.repository.UtilityTypeRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeterProvisioningServiceTest {

    @Mock
    private MeterRepository meters;

    @Mock
    private UtilityTypeRepository utilityTypes;

    @InjectMocks
    private MeterProvisioningService service;

    private static UtilityType type(long id, String name, String unit, String price) {
        UtilityType ut = new UtilityType();
        ut.setId(id);
        ut.setName(name);
        ut.setUnit(unit);
        ut.setUnitPrice(new BigDecimal(price));
        return ut;
    }

    @Test
    void ensureDefaultMetersForRoom_roomWithoutMeters_createsRandomNonZeroElectricAndWater() {
        UtilityType elec = type(1L, "Điện (Seed)", "kWh", "3500.00");
        UtilityType water = type(2L, "Nước (Seed)", "m3", "12000.00");
        when(meters.findByRoomId(105L)).thenReturn(List.of());
        when(utilityTypes.findAll()).thenReturn(List.of(elec, water));
        when(meters.save(any(Meter.class))).thenAnswer(inv -> inv.getArgument(0));
        when(meters.findByRoomIdAndIsActiveTrue(105L)).thenReturn(List.of());

        service.ensureDefaultMetersForRoom(105L, 2L);

        ArgumentCaptor<Meter> captor = ArgumentCaptor.forClass(Meter.class);
        verify(meters, times(2)).save(captor.capture());
        List<Meter> saved = captor.getAllValues();
        Meter savedElectric = saved.stream().filter(m -> m.getUtilityType().getId().equals(1L)).findFirst().orElseThrow();
        Meter savedWater = saved.stream().filter(m -> m.getUtilityType().getId().equals(2L)).findFirst().orElseThrow();

        assertThat(savedElectric.getInitialIndex()).isBetween(new BigDecimal("180"), new BigDecimal("2500"));
        assertThat(savedElectric.getInitialIndexDate()).isNull();
        assertThat(savedElectric.isActive()).isTrue();
        assertThat(savedElectric.getRoomId()).isEqualTo(105L);
        assertThat(savedElectric.getBuildingId()).isEqualTo(2L);

        assertThat(savedWater.getInitialIndex()).isBetween(new BigDecimal("18"), new BigDecimal("350"));
        assertThat(savedWater.getInitialIndexDate()).isNull();
        assertThat(savedWater.isActive()).isTrue();
    }

    @Test
    void ensureDefaultMetersForRoom_bothMetersAlreadyExist_doesNotOverwriteExistingMeters() {
        Meter existingElectric = new Meter();
        existingElectric.setId(11L);
        existingElectric.setUtilityType(type(1L, "Điện (Seed)", "kWh", "3500"));
        existingElectric.setInitialIndex(new BigDecimal("999"));
        existingElectric.setActive(true);

        Meter existingWater = new Meter();
        existingWater.setId(12L);
        existingWater.setUtilityType(type(2L, "Nước (Seed)", "m3", "12000"));
        existingWater.setInitialIndex(new BigDecimal("88"));
        existingWater.setActive(true);

        when(meters.findByRoomId(101L)).thenReturn(List.of(existingElectric, existingWater));

        List<Meter> result = service.ensureDefaultMetersForRoom(101L, 2L);

        verify(meters, never()).save(any());
        assertThat(result).hasSize(2);
    }
}

