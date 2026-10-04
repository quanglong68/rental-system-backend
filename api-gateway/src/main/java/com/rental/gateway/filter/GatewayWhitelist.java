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

    private static final List<String> PUBLIC_GET = List.of("/api/v1/locations/**", "/api/v1/buildings/nearby",
            "/api/v1/listings", "/api/v1/listings/*");

    private final AntPathMatcher matcher = new AntPathMatcher();

    public boolean isPublic(HttpMethod method, String path) {
        if (method == HttpMethod.POST && PUBLIC_POST.contains(path)) {
            return true;
        }
        if (method == HttpMethod.GET) {
            return PUBLIC_GET.stream().anyMatch(pattern -> matcher.match(pattern, path));
        }
        return false;
    }
}