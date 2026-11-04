package com.rental.chat.client;

import com.rental.common.feign.FeignDefaultConfig;
import java.time.Instant;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "listing-booking-service", configuration = FeignDefaultConfig.class)
public interface ListingBookingClient {

    record RoommateContext(Long applicationId, Long postId, Long ownerCustomerId, Long applicantCustomerId,
            String postTitle, Long roomId, Long buildingId, String roomNumber, String buildingName) {
    }

    record RoommatePostContext(Long postId, Long ownerCustomerId, String postTitle, Long roomId, Long buildingId,
            String roomNumber, String buildingName) {
    }

    record ListingContext(Long listingId, Long buildingId, Long roomId, String roomNumber, String buildingName,
            String title) {
    }

    record AppointmentContext(Long appointmentId, Long customerId, Long staffId, Long buildingId, Long roomId,
            Instant appointmentDate, String status, String roomNumber, String buildingName) {
    }

    record RentalRequestContext(Long rentalRequestId, Long customerId, Long listingId, Long buildingId,
            Long roomId, String status, Long verifiedBy, String roomNumber, String buildingName) {
    }

    @GetMapping("/internal/chat/roommate-applications/{applicationId}")
    RoommateContext roommateApplication(@PathVariable("applicationId") Long applicationId);

    @GetMapping("/internal/chat/roommate-posts/{postId}")
    RoommatePostContext roommatePost(@PathVariable("postId") Long postId);

    @GetMapping("/internal/chat/listings/{listingId}")
    ListingContext listing(@PathVariable("listingId") Long listingId);

    @GetMapping("/internal/chat/appointments/{appointmentId}")
    AppointmentContext appointment(@PathVariable("appointmentId") Long appointmentId);

    @GetMapping("/internal/chat/rental-requests/{rentalRequestId}")
    RentalRequestContext rentalRequest(@PathVariable("rentalRequestId") Long rentalRequestId);
}
