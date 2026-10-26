package fptu.semester8.mss301.propertiesservice.features.roomprice.application;

import fptu.exe202.signify.apiresponse.exception.BadRequestException;
import fptu.semester8.mss301.propertiesservice.common.id.IdGenerator;
import fptu.semester8.mss301.propertiesservice.features.events.PropertyEventPublisher;
import fptu.semester8.mss301.propertiesservice.features.room.infrastructure.persistence.RoomRepository;
import fptu.semester8.mss301.propertiesservice.features.roomprice.api.dto.SetRoomPriceRequest;
import fptu.semester8.mss301.propertiesservice.features.roomprice.domain.RoomPrice;
import fptu.semester8.mss301.propertiesservice.features.roomprice.infrastructure.RoomPriceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RoomPriceServiceEventTest {
    private final RoomPriceRepository prices = mock(RoomPriceRepository.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final IdGenerator ids = mock(IdGenerator.class);
    private final PropertyEventPublisher events = mock(PropertyEventPublisher.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final RoomPriceService service = new RoomPriceService(prices, rooms, ids, events, jdbc);

    @Test
    void todayPriceIsQueuedInOutbox() {
        when(rooms.existsById(5L)).thenReturn(true);
        when(ids.nextId()).thenReturn(11L);
        when(prices.findCoveringDate(any(), any())).thenReturn(Optional.empty());
        when(prices.findFirstByRoomIdAndEffectiveFromAfterOrderByEffectiveFromAsc(any(), any()))
                .thenReturn(Optional.empty());
        when(prices.saveAndFlush(any(RoomPrice.class))).thenAnswer(call -> call.getArgument(0));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

        service.setPrice(5L, new SetRoomPriceRequest(new BigDecimal("5000000"), today));

        verify(events).roomPriceChanged(any(RoomPrice.class));
        verify(jdbc).update(any(String.class), eq(11L));
    }

    @Test
    void futurePriceWaitsUntilItsEffectiveDate() {
        when(rooms.existsById(5L)).thenReturn(true);
        when(prices.findCoveringDate(any(), any())).thenReturn(Optional.empty());
        when(prices.findFirstByRoomIdAndEffectiveFromAfterOrderByEffectiveFromAsc(any(), any()))
                .thenReturn(Optional.empty());
        when(prices.saveAndFlush(any(RoomPrice.class))).thenAnswer(call -> call.getArgument(0));

        service.setPrice(5L, new SetRoomPriceRequest(BigDecimal.ONE,
                LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1)));

        verifyNoInteractions(events, jdbc);
    }

    @Test
    void pastDateIsRejectedBeforeSaving() {
        when(rooms.existsById(5L)).thenReturn(true);
        assertThrows(BadRequestException.class, () -> service.setPrice(5L,
                new SetRoomPriceRequest(BigDecimal.ONE,
                        LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1))));
        verifyNoInteractions(prices, events, jdbc);
    }
}
