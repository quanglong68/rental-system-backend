package fptu.semester8.mss301.propertiesservice.config;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;

/** Only the Gateway may forward identity-bearing requests to Property APIs. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PropertyGatewayTrustFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Property-Gateway-Key";

    private final byte[] expectedKey;
    private final ObjectMapper json;

    public PropertyGatewayTrustFilter(@Value("${property.security.gateway-key:}") String key, ObjectMapper json) {
        this.expectedKey = key.getBytes(StandardCharsets.UTF_8);
        if (expectedKey.length < 32) {
            throw new IllegalStateException("PROPERTY_GATEWAY_SECRET must contain at least 32 bytes");
        }
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var values = Collections.list(request.getHeaders(HEADER));
        if (values.size() != 1 || !MessageDigest.isEqual(expectedKey, values.getFirst().getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(json.writeValueAsString(ApiResponse.error(401, "Authentication required")));
            return;
        }
        chain.doFilter(request, response);
    }
}
