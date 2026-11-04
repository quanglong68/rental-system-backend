package com.rental.listingbooking.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.listingbooking.entity.ProcessedEvent;
import com.rental.listingbooking.entity.RoomListing;
import com.rental.listingbooking.repository.ProcessedEventRepository;
import com.rental.listingbooking.repository.RoomListingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class PropertyEventConsumer {

    private final RoomListingRepository listingRepository;
    private final ProcessedEventRepository processedEvents;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "property.events", groupId = "listing-booking-service")
    public void consume(String message) {
        JsonNode root;
        try {
            root = objectMapper.readTree(message);
        } catch (Exception ex) {
            log.error("Lỗi parse JSON event từ property.events: {}", message, ex);
            return;
        }

        String eventType = text(root, "eventType");
        String eventIdStr = text(root, "eventId");
        if (eventType == null || eventIdStr == null) {
            log.warn("Bỏ qua event thiếu eventType hoặc eventId: {}", message);
            return;
        }

        UUID eventId;
        try {
            eventId = UUID.fromString(eventIdStr);
        } catch (Exception ex) {
            log.warn("eventId không phải UUID hợp lệ: {}", eventIdStr);
            return;
        }

        JsonNode payload = root.path("payload");
        Instant occurredAt = parseInstant(text(root, "occurredAt"));

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            if (processedEvents.existsById(eventIdStr)) {
                log.info("Event {} đã được xử lý (idempotent), bỏ qua", eventIdStr);
                return;
            }

            switch (eventType) {
                case "RoomStatusChanged" -> handleRoomStatusChanged(payload, occurredAt);
                case "RoomDeleted" -> handleRoomDeleted(payload);
                case "RoomPriceChanged" -> handleRoomPriceChanged(payload);
                case "BuildingUpdated" -> handleBuildingUpdated(payload);
                default -> log.debug("Bỏ qua eventType: {}", eventType);
            }

            processedEvents.save(new ProcessedEvent(eventIdStr));
        });
    }

    private void handleRoomStatusChanged(JsonNode payload, Instant occurredAt) {
        Long roomId = requiredLong(payload, "roomId");
        String roomStatus = text(payload, "status");
        if (roomId == null || roomStatus == null) return;

        // Spec 9.3: a VACANT/UPCOMING_VACANT room must have an ACTIVE listing; OCCUPIED/MAINTENANCE
        // closes it. Any other value is not a listing-visible state.
        boolean rentable;
        if ("VACANT".equalsIgnoreCase(roomStatus) || "UPCOMING_VACANT".equalsIgnoreCase(roomStatus)) {
            rentable = true;
        } else if ("OCCUPIED".equalsIgnoreCase(roomStatus) || "MAINTENANCE".equalsIgnoreCase(roomStatus)) {
            rentable = false;
        } else {
            log.warn("Bỏ qua RoomStatusChanged với trạng thái không hỗ trợ: roomId={}, status={}", roomId, roomStatus);
            return;
        }

        RoomListing listing = listingRepository.findByRoomId(roomId).orElse(null);
        // Outbox co the phat event tre hon event da ap dung (commit dao thu tu), nen bo qua event cu
        // de khong mo lai listing da dong hoac nguoc lai.
        if (listing != null && occurredAt != null && listing.getRoomStatusEventAt() != null
                && !occurredAt.isAfter(listing.getRoomStatusEventAt())) {
            log.info("Bỏ qua RoomStatusChanged cũ: roomId={}, occurredAt={}, appliedAt={}",
                    roomId, occurredAt, listing.getRoomStatusEventAt());
            return;
        }

        boolean isNew = listing == null;
        if (isNew) {
            if (!rentable) return; // Nothing to close; a later VACANT event creates the listing.
            listing = new RoomListing();
            listing.setRoomId(roomId);
            listing.setBuildingId(optionalLong(payload, "buildingId"));
            listing.setBuildingName(text(payload, "buildingName"));
            listing.setAddress(text(payload, "address"));
            listing.setWardId(optionalLong(payload, "wardId"));
            listing.setDistrictId(optionalLong(payload, "districtId"));
            listing.setRoomTypeId(optionalLong(payload, "roomTypeId"));
            listing.setRoomTypeName(text(payload, "roomTypeName"));
            listing.setRoomNumber(text(payload, "roomNumber"));
            listing.setMaxCapacity(optionalInt(payload, "maxCapacity", 2));
            listing.setPrice(optionalDecimal(payload, "price", BigDecimal.ZERO));
            listing.setLatitude(optionalDecimal(payload, "latitude", null));
            listing.setLongitude(optionalDecimal(payload, "longitude", null));
            listing.setTitle(title(listing.getRoomNumber(), listing.getBuildingName()));
            applyLessorSnapshot(listing, payload);
            listing.setCreatedAt(Instant.now());
        } else if (!rentable && !"ACTIVE".equalsIgnoreCase(listing.getStatus())) {
            // Already closed; keep the row and its snapshot unchanged.
            return;
        }

        BigDecimal currentPrice = optionalDecimal(payload, "price", null);
        if (currentPrice != null) listing.setPrice(currentPrice);
        listing.setRoomStatus(roomStatus.toUpperCase(java.util.Locale.ROOT));
        listing.setStatus(rentable ? "ACTIVE" : "CLOSED");
        if (occurredAt != null) listing.setRoomStatusEventAt(occurredAt);
        listing.setUpdatedAt(Instant.now());

        listingRepository.save(listing);
        log.info("Cập nhật RoomListing theo RoomStatusChanged: roomId={}, roomStatus={}, listingStatus={}",
                roomId, roomStatus, listing.getStatus());
    }

    /** Spec 9.3: listing title is {@code Phòng {roomNumber} - {buildingName}}. */
    private static String title(String roomNumber, String buildingName) {
        String room = roomNumber == null || roomNumber.isBlank() ? "" : roomNumber;
        String building = buildingName == null || buildingName.isBlank() ? "" : buildingName;
        if (building.isEmpty()) return "Phòng " + room;
        return "Phòng " + room + " - " + building;
    }

    private void handleRoomDeleted(JsonNode payload) {
        Long roomId = requiredLong(payload, "roomId");
        if (roomId == null) return;

        listingRepository.findByRoomId(roomId).ifPresent(listing -> {
            if ("CLOSED".equalsIgnoreCase(listing.getStatus())) return;
            listing.setStatus("CLOSED");
            listing.setUpdatedAt(Instant.now());
            listingRepository.save(listing);
            log.info("Đóng RoomListing vì phòng đã bị xóa: roomId={}, listingId={}", roomId, listing.getId());
        });
    }

    private void handleRoomPriceChanged(JsonNode payload) {
        Long roomId = requiredLong(payload, "roomId");
        BigDecimal price = optionalDecimal(payload, "price", null);
        if (roomId != null && price != null) {
            listingRepository.findByRoomId(roomId).ifPresent(listing -> {
                listing.setPrice(price);
                listing.setUpdatedAt(Instant.now());
                listingRepository.save(listing);
                log.info("Cập nhật giá RoomListing: roomId={}, newPrice={}", roomId, price);
            });
        }
    }

    private void handleBuildingUpdated(JsonNode payload) {
        Long buildingId = requiredLong(payload, "buildingId");
        if (buildingId == null) return;

        // Property emits `name`; older payloads used `buildingName`.
        String name = text(payload, "name");
        String buildingName = name != null ? name : text(payload, "buildingName");
        String address = text(payload, "address");
        BigDecimal lat = optionalDecimal(payload, "latitude", null);
        BigDecimal lng = optionalDecimal(payload, "longitude", null);

        // Cập nhật các listing thuộc building này
        listingRepository.findManage(buildingId, null, org.springframework.data.domain.Pageable.unpaged())
                .forEach(listing -> {
                    if (buildingName != null) listing.setBuildingName(buildingName);
                    if (address != null) listing.setAddress(address);
                    if (lat != null) listing.setLatitude(lat);
                    if (lng != null) listing.setLongitude(lng);
                    applyLessorSnapshot(listing, payload);
                    listing.setUpdatedAt(Instant.now());
                    listingRepository.save(listing);
                });
    }

    private static void applyLessorSnapshot(RoomListing listing, JsonNode payload) {
        String lessorName = text(payload, "lessorName");
        if (lessorName == null) return;
        listing.setLessorName(lessorName);
        listing.setLessorDateOfBirth(optionalDate(payload, "lessorDateOfBirth"));
        listing.setLessorIdNumber(text(payload, "lessorIdNumber"));
        listing.setLessorIdIssueDate(optionalDate(payload, "lessorIdIssueDate"));
        listing.setLessorIdIssuePlace(text(payload, "lessorIdIssuePlace"));
        listing.setLessorPhone(text(payload, "lessorPhone"));
        listing.setLessorAddress(text(payload, "lessorAddress"));
    }

    private static java.time.LocalDate optionalDate(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) return null;
        try {
            return java.time.LocalDate.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static Long requiredLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asLong();
    }

    private static Long optionalLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asLong();
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Integer optionalInt(JsonNode node, String field, int def) {        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? def : value.asInt();
    }

    private static BigDecimal optionalDecimal(JsonNode node, String field, BigDecimal def) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return def;
        try {
            return new BigDecimal(value.asText());
        } catch (Exception ex) {
            return def;
        }
    }
}
