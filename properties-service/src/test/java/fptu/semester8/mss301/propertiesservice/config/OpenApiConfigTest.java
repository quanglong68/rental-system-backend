package fptu.semester8.mss301.propertiesservice.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenApiConfigTest {
    @Test
    void providesServiceApiMetadata() {
        var openApi = new OpenApiConfig().propertiesServiceOpenApi();

        assertEquals("Room Rental Properties Service API", openApi.getInfo().getTitle());
        assertEquals("v1", openApi.getInfo().getVersion());
    }
}
