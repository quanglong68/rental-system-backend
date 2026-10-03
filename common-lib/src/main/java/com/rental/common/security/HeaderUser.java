package com.rental.common.security;

import java.util.List;
import lombok.Value;

/**
 * Thong tin nguoi dung Gateway gan vao header sau khi verify JWT (docs muc 6, Table 6).
 * Lam principal trong SecurityContext; service dung voi
 * {@code @PreAuthorize("hasAnyRole('QUAN_LY','ADMIN')")} (can @EnableMethodSecurity).
 */
@Value
public class HeaderUser {

    /** X-User-Id (claim sub = account id). Vi du: 1024 */
    String userId;

    /** X-User-Type: CUSTOMER, STAFF, ADMIN (SYSTEM khi scheduler goi noi bo). */
    String userType;

    /** Danh sach role tu X-User-Roles (da tach dau phay, chua prefix ROLE_). */
    List<String> roles;
}
