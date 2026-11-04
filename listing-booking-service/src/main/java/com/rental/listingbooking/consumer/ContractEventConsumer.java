package com.rental.listingbooking.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.listingbooking.config.ListingAvailabilitySettings;
import com.rental.listingbooking.entity.ProcessedEvent;
import com.rental.listingbooking.entity.RentalRequest;
import com.rental.listingbooking.entity.RoomListing;
import com.rental.listingbooking.entity.RoommateApplication;
import com.rental.listingbooking.entity.RoommatePost;
import com.rental.listingbooking.outbox.ListingEventPublisher;
import com.rental.listingbooking.outbox.RentalRequestRejectedPayload;
import com.rental.listingbooking.repository.ProcessedEventRepository;
import com.rental.listingbooking.repository.RentalRequestRepository;
import com.rental.listingbooking.repository.RoomListingRepository;
import com.rental.listingbooking.repository.RoommateApplicationRepository;
import com.rental.listingbooking.repository.RoommatePostRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
public class ContractEventConsumer {

    private final RoomListingRepository listingRepository;
    private final RentalRequestRepository rentalRequestRepository;
    private final RoommatePostRepository roommatePostRepository;
    private final RoommateApplicationRepository applicationRepository;
    private final ProcessedEventRepository processedEvents;
    private final ListingEventPublisher publisher;
    private final ListingAvailabilitySettings availabilitySettings;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "contract.events", groupId = "listing-booking-service")
    public void consume(String message) {
        JsonNode root;
        try {
            root = objectMapper.readTree(message);
        } catch (Exception ex) {
            log.error("Lỗi parse JSON event từ contract.events: {}", message, ex);
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

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.executeWithoutResult(status -> {
            if (processedEvents.existsById(eventIdStr)) {
                log.info("Event {} đã được xử lý (idempotent), bỏ qua", eventIdStr);
                return;
            }

            switch (eventType) {
                case "ContractSigned" -> handleContractSigned(payload);
                case "ContractSignatureDeclined" -> handleSignatureDeclined(payload);
                case "ContractSignatureExpired" -> handleSignatureExpired(payload);
                case "TenantAdded" -> handleTenantAdded(payload);
                case "TenantAddRejected" -> handleTenantAddRejected(payload);
                case "MoveOutApproved" -> handleRoomRelease(payload, "expectedMoveOutDate");
                case "ContractEnded" -> handleContractEnded(payload);
                default -> log.debug("Bỏ qua eventType: {}", eventType);
            }

            processedEvents.save(new ProcessedEvent(eventIdStr));
        });
    }

    private void handleContractSigned(JsonNode payload) {
        Long roomId = requiredLong(payload, "roomId");
        if (roomId == null) return;

        // 1. Đóng RoomListing
        listingRepository.findByRoomId(roomId).ifPresent(listing -> {
            listing.setStatus("CLOSED");
            listing.setRoomStatus("OCCUPIED");
            listing.setUpdatedAt(Instant.now());
            listingRepository.save(listing);
            log.info("Đã đóng tin đăng phòng do hợp đồng đã ký: roomId={}", roomId);
        });

        // 2. Từ chối các yêu cầu thuê khác đang mở cho phòng này, kèm lý do rõ ràng.
        //    Yêu cầu đã sinh ra hợp đồng vừa ký phải giữ nguyên ACCEPTED.
        Long signedRequestId = requiredLong(payload, "rentalRequestId");
        List<Long> tenantIds = new ArrayList<>();
        JsonNode tenantIdsNode = payload.path("tenantIds");
        if (tenantIdsNode.isArray()) {
            for (JsonNode tNode : tenantIdsNode) {
                tenantIds.add(tNode.asLong());
            }
        }

        String startDate = text(payload, "startDate");
        String reason = startDate != null && !startDate.isBlank()
                ? "Phòng đã có người thuê trước. Hợp đồng có hiệu lực từ " + startDate + "."
                : "Phòng đã có người thuê trước. Hợp đồng đã có hiệu lực.";
        List<RentalRequest> openRequests = rentalRequestRepository
                .findByRoomIdAndStatusIn(roomId, List.of("PENDING", "VERIFIED", "ACCEPTED"));
        for (RentalRequest req : openRequests) {
            if (signedRequestId != null && req.getId().equals(signedRequestId)) continue;
            // Bảo hiểm: Nếu signedRequestId bị null hoặc thiếu trong payload, giữ nguyên yêu cầu ACCEPTED của chính người thuê vừa ký
            if (signedRequestId == null && "ACCEPTED".equals(req.getStatus()) && tenantIds.contains(req.getCustomerId())) {
                continue;
            }
            req.setStatus("REJECTED");
            req.setRejectReason(reason);
            rentalRequestRepository.save(req);
            publisher.publishRentalRequestRejected(new RentalRequestRejectedPayload(
                    req.getId(),
                    req.getCustomerId(),
                    reason
            ));
        }

        // 3. Đóng các tin tìm bạn ở ghép của phòng nếu đang OPEN
        List<RoommatePost> posts = roommatePostRepository.findByRoomIdAndStatus(roomId, "OPEN");
        for (RoommatePost p : posts) {
            p.setStatus("CLOSED");
            roommatePostRepository.save(p);
        }
    }

    /** The customer refused to sign: the request returns to VERIFIED so staff can approve it again. */
    private void handleSignatureDeclined(JsonNode payload) {
        Long requestId = requiredLong(payload, "rentalRequestId");
        if (requestId == null) return;
        rentalRequestRepository.findById(requestId).ifPresent(req -> {
            if ("ACCEPTED".equals(req.getStatus())) {
                req.setStatus("VERIFIED");
                rentalRequestRepository.save(req);
                log.info("Khách từ chối ký, đưa yêu cầu thuê về VERIFIED: requestId={}", requestId);
            }
        });
    }

    /** Nobody signed before the deadline: the request is rejected with a reason the customer can act on. */
    private void handleSignatureExpired(JsonNode payload) {
        Long requestId = requiredLong(payload, "rentalRequestId");
        if (requestId == null) return;
        rentalRequestRepository.findById(requestId).ifPresent(req -> {
            if (!List.of("PENDING", "VERIFIED", "ACCEPTED").contains(req.getStatus())) return;
            String reason = "Hết hạn ký hợp đồng nên yêu cầu thuê đã bị hủy. "
                    + "Vui lòng gửi lại yêu cầu thuê nếu bạn vẫn muốn thuê phòng.";
            req.setStatus("REJECTED");
            req.setRejectReason(reason);
            rentalRequestRepository.save(req);
            publisher.publishRentalRequestRejected(new RentalRequestRejectedPayload(
                    req.getId(), req.getCustomerId(), reason));
            log.info("Hợp đồng quá hạn ký, từ chối yêu cầu thuê: requestId={}", requestId);
        });
    }

    private void handleTenantAdded(JsonNode payload) {
        Long applicationId = requiredLong(payload, "applicationId");
        if (applicationId != null) {
            applicationRepository.findById(applicationId).ifPresent(app -> {
                if ("ACCEPTED_PENDING".equals(app.getStatus())) {
                    app.setStatus("ACCEPTED");
                    applicationRepository.save(app);
                    log.info("Cập nhật đơn ở ghép sang ACCEPTED: applicationId={}", applicationId);
                }
            });
            return;
        }

        Long customerId = requiredLong(payload, "customerId");
        if (customerId == null) return;
        Long roomId = requiredLong(payload, "roomId");

        // Legacy events do not carry applicationId, so scope the fallback to this room: a manual
        // staff addTenant must never accept a roommate application that belongs to another room.
        List<RoommateApplication> apps = applicationRepository.findByCustomerId(customerId);
        for (RoommateApplication app : apps) {
            if (!"ACCEPTED_PENDING".equals(app.getStatus())) continue;
            if (roomId != null) {
                RoommatePost post = roommatePostRepository.findById(app.getRoommatePostId()).orElse(null);
                if (post == null || !roomId.equals(post.getRoomId())) continue;
            }
            app.setStatus("ACCEPTED");
            applicationRepository.save(app);
            log.info("Cập nhật đơn ở ghép sang ACCEPTED: applicationId={}, customerId={}", app.getId(), customerId);
        }
    }

    private void handleTenantAddRejected(JsonNode payload) {        Long applicationId = requiredLong(payload, "applicationId");
        if (applicationId != null) {
            applicationRepository.findById(applicationId).ifPresent(app -> {
                if ("ACCEPTED_PENDING".equals(app.getStatus())) {
                    app.setStatus("REJECTED_FULL");
                    applicationRepository.save(app);
                    log.info("Cập nhật đơn ở ghép sang REJECTED_FULL: applicationId={}", applicationId);
                }
            });
            return;
        }

        Long customerId = requiredLong(payload, "applicantCustomerId");
        if (customerId == null) customerId = requiredLong(payload, "customerId");
        if (customerId == null) return;

        List<RoommateApplication> apps = applicationRepository.findByCustomerId(customerId);
        for (RoommateApplication app : apps) {
            if ("ACCEPTED_PENDING".equals(app.getStatus())) {
                app.setStatus("REJECTED_FULL");
                applicationRepository.save(app);
                log.info("Cập nhật đơn ở ghép sang REJECTED_FULL: applicationId={}, customerId={}", app.getId(), customerId);
            }
        }
    }

    /**
     * Spec 13.5: after an approved move-out the room can be advertised again from the move-out date
     * plus the cleaning buffer, as UPCOMING_VACANT until that day arrives.
     */
    private void handleRoomRelease(JsonNode payload, String dateField) {
        Long roomId = requiredLong(payload, "roomId");
        LocalDate releaseDate = parseDate(text(payload, dateField));
        if (roomId == null || releaseDate == null) {
            log.warn("Bỏ qua sự kiện giải phóng phòng thiếu roomId hoặc {}: {}", dateField, payload);
            return;
        }
        reopenListing(roomId, releaseDate);
    }

    /** A contract ending without a following contract makes the room rentable again. */
    private void handleContractEnded(JsonNode payload) {
        Long roomId = requiredLong(payload, "roomId");
        if (roomId == null) return;
        if (payload.path("nextContractExists").asBoolean(false)) {
            log.info("Hợp đồng kết thúc nhưng đã có hợp đồng kế tiếp, giữ đóng tin đăng: roomId={}", roomId);
            return;
        }
        LocalDate endDate = parseDate(text(payload, "endDate"));
        reopenListing(roomId, endDate == null ? LocalDate.now() : endDate);
    }

    private void reopenListing(Long roomId, LocalDate referenceDate) {
        RoomListing listing = listingRepository.findByRoomId(roomId).orElse(null);
        if (listing == null) {
            // The property projection creates the listing later from RoomStatusChanged; it cannot know
            // the buffer, so log it for reconciliation instead of inventing missing snapshots here.
            log.warn("Không tìm thấy tin đăng để mở lại cho phòng {} (ngày có thể vào {})", roomId, referenceDate);
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate availableFrom = referenceDate.plusDays(availabilitySettings.reopenBufferDays());
        if (availableFrom.isBefore(today)) {
            availableFrom = today;
        }
        listing.setStatus("ACTIVE");
        listing.setRoomStatus(availableFrom.isAfter(today) ? "UPCOMING_VACANT" : "VACANT");
        listing.setAvailableFrom(availableFrom);
        listing.setUpdatedAt(Instant.now());
        listingRepository.save(listing);
        log.info("Mở lại tin đăng phòng {}: availableFrom={}, roomStatus={}",
                roomId, availableFrom, listing.getRoomStatus());
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
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
}
