package com.rental.propertiesservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.common.constant.Headers;
import com.rental.common.error.ErrorCode;
import com.rental.common.error.ErrorResponse;
import com.rental.common.security.HeaderUser;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Authorization for property endpoints. Role checks for write operations live on the controllers
 * ({@code @PreAuthorize}, spec 6.2); these chains only separate public reads, occupant reads and
 * authenticated routes, rebuilding identity from the Gateway headers via
 * {@link PropertyHeaderAuthenticationFilter}.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String STATISTICS_PATH = "/api/v1/rooms/statistics";
    private static final Pattern ROOM_READ =
            Pattern.compile("/api/v1/rooms/[^/]+/(occupants(?:/.*)?|occupancy-summary)");
    private static final Pattern USER_READ = Pattern.compile("/api/v1/rooms/occupancies/users/([^/]+)");
    private static final Set<String> STAFF_ROLES = Set.of("ROLE_QUAN_LY", "ROLE_SALE", "ROLE_KY_THUAT");

    @Bean
    @Order(1)
    public SecurityFilterChain occupantReadSecurity(HttpSecurity http) throws Exception {
        RequestMatcher occupantReads = request -> {
            String path = path(request.getRequestURI(), request.getContextPath());
            return path.equals(STATISTICS_PATH) || ROOM_READ.matcher(path).matches()
                    || USER_READ.matcher(path).matches();
        };

        ObjectMapper json = new ObjectMapper();
        http.securityMatcher(occupantReads)
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().access((authentication, context) -> {
                            Authentication user = authentication.get();
                            String path = path(context.getRequest().getRequestURI(),
                                    context.getRequest().getContextPath());
                            if (!HttpMethod.GET.matches(context.getRequest().getMethod())) {
                                return new org.springframework.security.authorization.AuthorizationDecision(false);
                            }
                            if (user == null || user instanceof AnonymousAuthenticationToken) {
                                return new org.springframework.security.authorization.AuthorizationDecision(false);
                            }
                            boolean admin = hasRole(user, "ROLE_ADMIN");
                            if (STATISTICS_PATH.equals(path)) {
                                return new org.springframework.security.authorization.AuthorizationDecision(admin);
                            }
                            if (ROOM_READ.matcher(path).matches()) {
                                boolean staff = user.getAuthorities().stream()
                                        .anyMatch(role -> STAFF_ROLES.contains(role.getAuthority()));
                                return new org.springframework.security.authorization.AuthorizationDecision(
                                        admin || staff);
                            }
                            var own = USER_READ.matcher(path);
                            boolean customer = hasRole(user, "ROLE_CUSTOMER");
                            return new org.springframework.security.authorization.AuthorizationDecision(
                                    admin || (customer && own.matches() && own.group(1).equals(userIdOf(user))));
                        }))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                                        ErrorCode.UNAUTHENTICATED, "Chưa đăng nhập", json))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(request, response, HttpServletResponse.SC_FORBIDDEN,
                                        ErrorCode.FORBIDDEN, "Không đủ quyền", json)))
                .addFilterBefore(new PropertyHeaderAuthenticationFilter(), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain propertySecurity(HttpSecurity http) throws Exception {
        ObjectMapper json = new ObjectMapper();
        http.csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/internal/**", "/actuator/**", "/v3/api-docs/**",
                                "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/locations/**", "/api/v1/provinces/**",
                                "/api/v1/wards/**", "/api/v1/room-types/**", "/api/v1/buildings/nearby").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/images/rooms/**",
                                "/api/v1/images/buildings/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/buildings/search").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/buildings/{buildingId}").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                writeError(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                                        ErrorCode.UNAUTHENTICATED, "Chưa đăng nhập", json))
                        .accessDeniedHandler((request, response, ex) ->
                                writeError(request, response, HttpServletResponse.SC_FORBIDDEN,
                                        ErrorCode.FORBIDDEN, "Không đủ quyền", json)))
                .addFilterBefore(new PropertyHeaderAuthenticationFilter(), AuthorizationFilter.class);
        return http.build();
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private static String userIdOf(Authentication authentication) {
        return authentication.getPrincipal() instanceof HeaderUser user
                ? user.getUserId() : authentication.getName();
    }

    private static String path(String requestUri, String contextPath) {
        return contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length()) : requestUri;
    }

    private static void writeError(jakarta.servlet.http.HttpServletRequest request,
            HttpServletResponse response, int status, ErrorCode code, String message, ObjectMapper json)
            throws IOException {
        String correlationId = request.getHeader(Headers.X_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ErrorResponse body = ErrorResponse.builder()
                .code(code.name())
                .message(message)
                .status(status)
                .traceId(correlationId)
                .build();
        json.writeValue(response.getWriter(), body);
    }
}
