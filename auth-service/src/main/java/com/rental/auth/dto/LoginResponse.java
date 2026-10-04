package com.rental.auth.dto;

import com.rental.auth.entity.AccountType;
import java.util.List;
import lombok.Value;

/**
 * Response dang nhap/refresh (docs Table 7): accessToken 15 phut + refreshToken 7 ngay.
 * account.roles la danh sach role thuc (khong co prefix ROLE_).
 */
@Value
public class LoginResponse {

    String accessToken;
    String refreshToken;
    String tokenType;
    long expiresIn;
    AccountInfo account;

    /** Thong tin tai khoan kem token (id, username, accountType, roles). */
    @Value
    public static class AccountInfo {

        Long id;
        String username;
        AccountType accountType;
        List<String> roles;
    }
}