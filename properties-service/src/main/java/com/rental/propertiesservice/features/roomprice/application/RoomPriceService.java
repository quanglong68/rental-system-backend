package com.rental.propertiesservice.features.roomprice.application;

import com.rental.common.error.BusinessException;
import com.rental.common.error.NotFoundException;
import org.springframework.http.HttpStatus;
import com.rental.propertiesservice.common.id.IdGenerator;
import com.rental.propertiesservice.config.BuildingAccessGuard;
import com.rental.propertiesservice.features.room.domain.Room;
import com.rental.propertiesservice.features.room.infrastructure.persistence.RoomRepository;
import com.rental.propertiesservice.features.events.PropertyEventPublisher;
import com.rental.propertiesservice.features.roomprice.api.dto.SetRoomPriceRequest;
import com.rental.propertiesservice.features.roomprice.domain.RoomPrice;
import com.rental.propertiesservice.features.roomprice.infrastructure.RoomPriceRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomPriceService {

    RoomPriceRepository prices;

    RoomRepository rooms;

    IdGenerator ids;

    PropertyEventPublisher events;

    BuildingAccessGuard access;

    JdbcTemplate jdbc;

    @Transactional
    public RoomPrice setPrice(long roomId, SetRoomPriceRequest request) {
        Room room = requireRoom(roomId);
        access.assertAssigned(room.getBuildingId());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        if (request.effectiveFrom().isBefore(today)) {
            throw new BusinessException("ROOM_PRICE_EFFECTIVE_FROM_IN_PAST", "effectiveFrom không được ở quá khứ", HttpStatus.BAD_REQUEST);
        }

        LocalDate effectiveFrom = request.effectiveFrom();
        Instant now = Instant.now();
        var existing = prices.findCoveringDate(roomId, effectiveFrom);
        if (existing.isPresent() && existing.get().getEffectiveFrom().isEqual(effectiveFrom)) {
            RoomPrice sameDate = existing.get();
            sameDate.changePrice(request.price(), now);
            RoomPrice saved = prices.saveAndFlush(sameDate);
            announceIfEffectiveToday(saved, today);
            return saved;
        }

        existing.ifPresent(current -> {
            current.closeOn(effectiveFrom.minusDays(1), now);
            prices.saveAndFlush(current);
        });

        LocalDate effectiveTo = prices
                .findFirstByRoomIdAndEffectiveFromAfterOrderByEffectiveFromAsc(roomId, effectiveFrom)
                .map(next -> next.getEffectiveFrom().minusDays(1))
                .orElse(null);

        RoomPrice saved = prices.saveAndFlush(new RoomPrice(ids.nextId(), roomId, request.price(),
                effectiveFrom, effectiveTo, now));
        announceIfEffectiveToday(saved, today);
        return saved;
    }

    @Transactional(readOnly = true)
    public RoomPrice getPriceForDate(long roomId, LocalDate date) {
        requireRoom(roomId);
        return prices.findCoveringDate(roomId, date).orElseThrow(() ->
                new NotFoundException("ROOM_PRICE_NOT_FOUND", "Không có giá phòng cho ngày yêu cầu"));
    }

    @Transactional(readOnly = true)
    public List<RoomPrice> history(long roomId) {
        Room room = requireRoom(roomId);
        access.assertAssigned(room.getBuildingId());
        return prices.findAllByRoomIdOrderByEffectiveFromDesc(roomId);
    }

    private Room requireRoom(long roomId) {
        return rooms.findById(roomId).orElseThrow(() ->
                new NotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng %d".formatted(roomId)));
    }

    private void announceIfEffectiveToday(RoomPrice price, LocalDate today) {
        if (price.getEffectiveFrom().isEqual(today)) {
            events.roomPriceChanged(price);
            jdbc.update("UPDATE room_price SET event_enqueued_at = CURRENT_TIMESTAMP WHERE room_price_id = ?",
                    price.getId());
        }
    }
}
