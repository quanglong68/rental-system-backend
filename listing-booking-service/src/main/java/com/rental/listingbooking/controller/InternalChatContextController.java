package com.rental.listingbooking.controller;

import com.rental.listingbooking.dto.ChatAppointmentContext;
import com.rental.listingbooking.dto.ChatListingContext;
import com.rental.listingbooking.dto.ChatRentalRequestContext;
import com.rental.listingbooking.dto.ChatRoommateContext;
import com.rental.listingbooking.dto.ChatRoommatePostContext;
import com.rental.listingbooking.service.InternalChatContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service only. Gateway khong route {@code /internal/**}.
 */
@RestController
@RequestMapping("/internal/chat")
@RequiredArgsConstructor
public class InternalChatContextController {

    private final InternalChatContextService service;

    @GetMapping("/roommate-applications/{applicationId}")
    public ChatRoommateContext roommateApplication(@PathVariable Long applicationId) {
        return service.roommateApplication(applicationId);
    }

    @GetMapping("/roommate-posts/{postId}")
    public ChatRoommatePostContext roommatePost(@PathVariable Long postId) {
        return service.roommatePost(postId);
    }

    @GetMapping("/listings/{listingId}")
    public ChatListingContext listing(@PathVariable Long listingId) {
        return service.listing(listingId);
    }

    @GetMapping("/appointments/{appointmentId}")
    public ChatAppointmentContext appointment(@PathVariable Long appointmentId) {
        return service.appointment(appointmentId);
    }

    @GetMapping("/rental-requests/{rentalRequestId}")
    public ChatRentalRequestContext rentalRequest(@PathVariable Long rentalRequestId) {
        return service.rentalRequest(rentalRequestId);
    }
}
