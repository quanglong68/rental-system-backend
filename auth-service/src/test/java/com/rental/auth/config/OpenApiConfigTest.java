package com.rental.auth.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenApiConfigTest {

    @Test
    void providesServiceApiMetadata() {
        var openApi = new OpenApiConfig().authServiceOpenApi();

        assertEquals("Room Rental Auth Service API", openApi.getInfo().getTitle());
        assertEquals("v1", openApi.getInfo().getVersion());
    }
}

