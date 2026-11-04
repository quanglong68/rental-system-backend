package com.rental.listingbooking.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Days between a contract ending and the room being advertised as available again. */
@Component
public class ListingAvailabilitySettings {

    private final int reopenBufferDays;

    public ListingAvailabilitySettings(@Value("${booking.reopen-buffer-days:10}") int reopenBufferDays) {
        this.reopenBufferDays = reopenBufferDays;
    }

    public int reopenBufferDays() {
        return reopenBufferDays;
    }
}
