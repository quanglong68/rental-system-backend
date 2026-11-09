package com.rental.gateway.filter;

import java.util.List;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

/**
 * Danh sach duong PUBLIC cho qua khong can token (docs muc 4.2, ke hoach D2).
 * POST dang nhap/refresh (client goi thang) + GET du lieu cong khai (locations,
 * buildings nearby, listings). Moi duong khac deu can Bearer token.
 */
public class GatewayWhitelist {

    private static final Set<String> PUBLIC_POST = Set.of("/api/v1/auth/register", "/api/v1/auth/login",
            "/api/v1/auth/refresh");

    private static final List<String> PUBLIC_ANY_METHOD = List.of(
            "/property-images/**",
            "/report-images/**",
            "/api/v1/payments/vnpay-ipn",
            "/api/v1/payments/vnpay-ipn/**",
            "/api/v1/payments/vnpay/confirm",
            "/api/v1/payments/vnpay/confirm/**");

    private static final List<String> PUBLIC_GET = List.of("/api/v1/locations/**", "/api/v1/buildings/nearby",
            "/api/v1/buildings/{id:[0-9]+}", "/api/v1/room-types/**", "/api/v1/provinces/**",
            "/api/v1/wards/**", "/api/v1/listings", "/api/v1/listings/{id:[0-9]+}",
            "/api/v1/listings/room/{roomId:[0-9]+}", "/api/v1/images/rooms/**",
            "/api/v1/images/buildings/**", "/swagger-ui.html", "/swagger-ui/**",
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-config");

    private final AntPathMatcher matcher = new AntPathMatcher();

    public boolean isPublic(HttpMethod method, String path) {
        if (PUBLIC_ANY_METHOD.stream().anyMatch(pattern -> matcher.match(pattern, path))) {
            return true;
        }
        if (method == HttpMethod.POST && PUBLIC_POST.contains(path)) {
            return true;
        }
        if (method == HttpMethod.GET) {
            return PUBLIC_GET.stream().anyMatch(pattern -> matcher.match(pattern, path));
        }
        return false;
    }
}
