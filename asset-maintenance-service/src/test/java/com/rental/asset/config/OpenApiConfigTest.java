package com.rental.asset.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenApiConfigTest {

    @Test
    void providesServiceApiMetadata() {
        var openApi = new OpenApiConfig().assetMaintenanceServiceOpenApi();

        assertEquals("Room Rental Asset & Maintenance Service API", openApi.getInfo().getTitle());
        assertEquals("v1", openApi.getInfo().getVersion());
    }
}

