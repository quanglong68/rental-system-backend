package com.rental.propertiesservice.features.room.api;

import com.rental.common.error.NotFoundException;
import com.rental.propertiesservice.features.building.domain.Building;
import com.rental.propertiesservice.features.building.infrastructure.persistence.repository.SpringDataBuildingRepository;
import com.rental.propertiesservice.features.room.domain.Room;
import com.rental.propertiesservice.features.room.infrastructure.persistence.RoomRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spec 9.2's one internal Property endpoint: {@code GET /internal/rooms/{roomId}} returning
 * {@code {id, buildingId, roomNumber, maxCapacity, status}}. Contract, Billing and Asset call it to
 * read capacity / building; the Gateway must keep {@code /internal/**} unreachable from clients.
 *
 * <p>The body is the raw record, not an envelope, because the callers' Feign DTOs declare the
 * documented shape verbatim; wrapping it would require them to unwrap a field the spec does not have.
 * Building name/address and lessor identity are included so contract-tenant-service can snapshot the
 * legal parties when a contract is created outside the rental-request event flow.
 */
@RestController
@RequestMapping("/internal/rooms")
@RequiredArgsConstructor
public class InternalRoomController {

    private final RoomRepository rooms;
    private final SpringDataBuildingRepository buildings;

    @GetMapping("/{roomId}")
    public RoomInternalResponse get(@PathVariable long roomId) {
        Room room = rooms.findById(roomId).orElseThrow(() ->
                new NotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng %d".formatted(roomId)));
        Building building = buildings.findByBuildingIdAndDeletedFalse(room.getBuildingId()).orElse(null);
        return new RoomInternalResponse(room.getId(), room.getBuildingId(), room.getRoomCode(),
                room.getMaxCapacity(), room.getStatus(),
                building == null ? null : building.getName(),
                building == null ? null : building.getAddress(),
                building == null ? null : building.getLessorName(),
                building == null ? null : building.getLessorDateOfBirth(),
                building == null ? null : building.getLessorIdNumber(),
                building == null ? null : building.getLessorIdIssueDate(),
                building == null ? null : building.getLessorIdIssuePlace(),
                building == null ? null : building.getLessorPhone(),
                building == null ? null : building.getLessorAddress());
    }

    public record RoomInternalResponse(Long id, Long buildingId, String roomNumber,
                                       Integer maxCapacity, String status,
                                       String buildingName, String buildingAddress,
                                       String lessorName, LocalDate lessorDateOfBirth,
                                       String lessorIdNumber, LocalDate lessorIdIssueDate,
                                       String lessorIdIssuePlace, String lessorPhone,
                                       String lessorAddress) {
    }
}
