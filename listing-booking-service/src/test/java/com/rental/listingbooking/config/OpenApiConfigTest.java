package com.rental.listingbooking.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenApiConfigTest {

    @Test
    void providesServiceApiMetadata() {
        var openApi = new OpenApiConfig().listingBookingServiceOpenApi();

        assertEquals("Room Rental Listing & Booking Service API", openApi.getInfo().getTitle());
        assertEquals("v1", openApi.getInfo().getVersion());
    }
}

