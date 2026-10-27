package fptu.semester8.mss301.propertiesservice.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.*;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Identity headers follow the existing Gateway contract. Direct backend access must be network restricted.
 * Building-scoped staff access is denied until a verified staff-building lookup exists in this service.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class OccupantSecurityConfig {
    private static final Pattern ROOM_READ = Pattern.compile("/api/v1/rooms/[^/]+/(occupants(?:/.*)?|occupancy-summary)");
    private static final Pattern USER_READ = Pattern.compile("/api/v1/rooms/occupancies/users/([^/]+)");

    @Bean
    @Order(1)
    public SecurityFilterChain occupantSecurity(HttpSecurity http, ObjectMapper json) throws Exception {
        http.securityMatcher(request -> ROOM_READ.matcher(path(request)).matches()
                        || USER_READ.matcher(path(request)).matches()
                        || "/api/v1/rooms/statistics".equals(path(request)))
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a.anyRequest().access((authentication, context) -> {
                    var auth = authentication.get();
                    if (!"GET".equals(context.getRequest().getMethod())) return new AuthorizationDecision(false);
                    boolean admin = auth.getAuthorities().stream().anyMatch(r -> r.getAuthority().equals("ROLE_ADMIN"));
                    var own = USER_READ.matcher(path(context.getRequest()));
                    boolean customer = auth.getAuthorities().stream().anyMatch(r -> r.getAuthority().equals("ROLE_CUSTOMER"));
                    return new AuthorizationDecision(admin || (customer && own.matches() && own.group(1).equals(auth.getName())));
                }))
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> error(json, res, 401, "Authentication required"))
                        .accessDeniedHandler((req, res, ex) -> error(json, res, 403, "Occupancy access denied")))
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                            throws ServletException, IOException {
                        try {
                            String rawId = req.getHeader("X-User-Id");
                            String type = req.getHeader("X-User-Type");
                            long id;
                            try { id = Long.parseLong(rawId); }
                            catch (RuntimeException invalid) { error(json, res, 401, "Authentication required"); return; }
                            if (id <= 0 || type == null || type.isBlank()) { error(json, res, 401, "Authentication required"); return; }
                            String rawRoles = req.getHeader("X-User-Roles");
                            var roles = Arrays.stream(rawRoles == null ? new String[0] : rawRoles.split(","))
                                    .map(String::trim).filter(r -> !r.isEmpty())
                                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(Long.toString(id), null, roles));
                            chain.doFilter(req, res);
                        } finally { SecurityContextHolder.clearContext(); }
                    }
                }, AuthorizationFilter.class);
        return http.build();
    }

    private static void error(ObjectMapper json, HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.writeValueAsString(java.util.Map.of("status", status, "message", message)));
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
