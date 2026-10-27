package fptu.semester8.mss301.propertiesservice.config;

import fptu.exe202.signify.apiresponse.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Arrays;

/** Fallback chain: every non-occupant API is covered and unknown paths are denied. */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class PropertyApiSecurityConfig {
    @Bean
    @Order(2)
    SecurityFilterChain propertyApiSecurity(HttpSecurity http, ObjectMapper json) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/internal/rooms/*").permitAll()
                        .requestMatchers("/internal/**").denyAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/buildings/nearby", "/api/v1/room-types").permitAll()
                        .requestMatchers(request -> request.getMethod().equals("GET")
                                && request.getServletPath().matches("/api/v1/buildings/[0-9]+"))
                            .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/buildings").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/buildings/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/buildings/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/buildings", "/api/v1/buildings/search")
                            .hasAnyRole("ADMIN", "QUAN_LY", "SALE", "KY_THUAT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/room-types/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/room-types").hasAnyRole("ADMIN", "QUAN_LY")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/room-types/*").hasAnyRole("ADMIN", "QUAN_LY")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/room-types/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/provinces/**", "/api/v1/wards/**").permitAll()
                        .requestMatchers("/api/v1/provinces/**", "/api/v1/wards/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/rooms/*/prices/current").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/rooms/*/prices/history")
                            .hasAnyRole("ADMIN", "QUAN_LY", "SALE", "KY_THUAT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/rooms/*/prices").hasAnyRole("ADMIN", "QUAN_LY")
                        .requestMatchers(HttpMethod.GET, "/api/v1/rooms/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/rooms", "/api/v1/rooms/building/*")
                            .hasAnyRole("ADMIN", "QUAN_LY", "SALE", "KY_THUAT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/rooms").hasAnyRole("ADMIN", "QUAN_LY")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/rooms/*").hasAnyRole("ADMIN", "QUAN_LY")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/rooms/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/images/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/images/buildings/*", "/api/v1/images/rooms/*")
                            .hasAnyRole("ADMIN", "QUAN_LY")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/images/buildings/*/*", "/api/v1/images/rooms/*/*")
                            .hasAnyRole("ADMIN", "QUAN_LY")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/images/buildings/*/*", "/api/v1/images/rooms/*/*")
                            .hasAnyRole("ADMIN", "QUAN_LY")
                        .requestMatchers(HttpMethod.POST, "/api/v1/images/*").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> error(json, res, 401, "Authentication required"))
                        .accessDeniedHandler((req, res, ex) -> error(json, res, 403, "Access denied")))
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override
                    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                            throws ServletException, IOException {
                        String id = req.getHeader("X-User-Id");
                        String type = req.getHeader("X-User-Type");
                        if (id == null && type == null) {
                            chain.doFilter(req, res);
                            return;
                        }
                        long parsed;
                        try { parsed = Long.parseLong(id); }
                        catch (RuntimeException invalid) { error(json, res, 401, "Authentication required"); return; }
                        if (parsed <= 0 || type == null || type.isBlank()) {
                            error(json, res, 401, "Authentication required"); return;
                        }
                        var roles = Arrays.stream(req.getHeader("X-User-Roles") == null
                                        ? new String[0] : req.getHeader("X-User-Roles").split(","))
                                .map(String::trim).filter(role -> !role.isEmpty())
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
                        try {
                            SecurityContextHolder.getContext().setAuthentication(
                                    new UsernamePasswordAuthenticationToken(Long.toString(parsed), null, roles));
                            chain.doFilter(req, res);
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                    }
                }, AuthorizationFilter.class)
                .build();
    }

    private static void error(ObjectMapper json, HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(json.writeValueAsString(ApiResponse.error(status, message)));
    }
}
