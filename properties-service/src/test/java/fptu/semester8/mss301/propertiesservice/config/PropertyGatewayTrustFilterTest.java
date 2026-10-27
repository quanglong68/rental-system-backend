package fptu.semester8.mss301.propertiesservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PropertyGatewayTrustFilterTest {
    private static final String KEY = "test-only-property-gateway-key-32-bytes-minimum";
    private final PropertyGatewayTrustFilter filter = new PropertyGatewayTrustFilter(KEY, new ObjectMapper());

    @Test
    void rejectsMissingKeyEvenWhenIdentityHeadersClaimAdmin() throws Exception {
        var request = apiRequest();
        request.addHeader("X-User-Id", "1");
        request.addHeader("X-User-Type", "ADMIN");
        request.addHeader("X-User-Roles", "ADMIN");
        var response = new MockHttpServletResponse();
        var forwarded = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> forwarded.set(true));
        assertEquals(401, response.getStatus());
        assertFalse(forwarded.get());
    }

    @Test
    void rejectsWrongAndDuplicateKeys() throws Exception {
        var request = apiRequest();
        request.addHeader(PropertyGatewayTrustFilter.HEADER, "wrong");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("untrusted request forwarded"));
        assertEquals(401, response.getStatus());

        request = apiRequest();
        request.addHeader(PropertyGatewayTrustFilter.HEADER, KEY);
        request.addHeader(PropertyGatewayTrustFilter.HEADER, "wrong");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("ambiguous request forwarded"));
        assertEquals(401, response.getStatus());
    }

    @Test
    void acceptsGatewayKeyAndLeavesHealthUnchanged() throws Exception {
        var request = apiRequest();
        request.addHeader(PropertyGatewayTrustFilter.HEADER, KEY);
        var response = new MockHttpServletResponse();
        var forwarded = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> forwarded.set(true));
        assertTrue(forwarded.get());

        var health = new MockHttpServletRequest("GET", "/actuator/health");
        filter.doFilter(health, new MockHttpServletResponse(), (req, res) -> forwarded.set(true));
        assertTrue(forwarded.get());
    }

    private static MockHttpServletRequest apiRequest() {
        var request = new MockHttpServletRequest("GET", "/api/v1/rooms/1/occupants");
        request.setServletPath("/api/v1/rooms/1/occupants");
        return request;
    }
}
