package fptu.semester8.mss301.propertiesservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;

/** Authenticates service-to-service requests separately from Gateway requests. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 11)
public class PropertyInternalAuthFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Property-Internal-Key";
    private final byte[] expected;

    public PropertyInternalAuthFilter(@Value("${property.security.internal-key:}") String key) {
        expected = key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var values = Collections.list(request.getHeaders(HEADER));
        if (expected.length < 32 || values.size() != 1
                || !MessageDigest.isEqual(expected, values.getFirst().getBytes(StandardCharsets.UTF_8))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }
}
