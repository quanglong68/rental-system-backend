package com.rental.user.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenApiConfigTest {

    @Test
    void providesServiceApiMetadata() {
        var openApi = new OpenApiConfig().userNotificationServiceOpenApi();

        assertEquals("Room Rental User & Notification Service API", openApi.getInfo().getTitle());
        assertEquals("v1", openApi.getInfo().getVersion());
    }
}

