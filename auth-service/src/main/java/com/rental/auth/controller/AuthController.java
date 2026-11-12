package com.rental.auth.controller;

import com.rental.auth.dto.ChangePasswordRequest;
import com.rental.auth.dto.LoginRequest;
import com.rental.auth.dto.LoginResponse;
import com.rental.auth.dto.MeResponse;
import com.rental.auth.dto.RefreshRequest;
import com.rental.auth.dto.RegisterRequest;
import com.rental.auth.dto.RegisterResponse;
import com.rental.auth.service.AccountService;
import com.rental.auth.service.AuthSessionService;
import com.rental.common.security.HeaderUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API auth (docs Table 7): register (Task C4) + login/refresh/logout/me/password (Task C5).
 * Duong PUBLIC khai bao tai {@link PublicPaths} va khai bao permitAll trong SecurityConfig.
 * Duong /logout, /me, /password can header X-User-* do Gateway gan.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Xác thực & Phiên đăng nhập", description = "Đăng ký, đăng nhập, làm mới JWT token, đăng xuất và đổi mật khẩu")
@RequiredArgsConstructor
public class AuthController {

    private final AccountService accountService;
    private final AuthSessionService sessionService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Đăng ký tài khoản khách hàng", description = "Tạo tài khoản mới với vai trò CUSTOMER; trả về thông tin tài khoản vừa tạo.")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
        return accountService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập hệ thống", description = "Xác thực username/email và mật khẩu, trả về accessToken (JWT) và refreshToken.")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return sessionService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Làm mới access token", description = "Dùng refreshToken còn hạn để cấp cặp token mới (token rotation).")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return sessionService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đăng xuất", description = "Thu hồi refreshToken hiện tại của người dùng.")
    public void logout(@Valid @RequestBody RefreshRequest request) {
        sessionService.logout(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Xem thông tin phiên đăng nhập hiện tại", description = "Trả về userId, userType và danh sách roles từ JWT đang đăng nhập.")
    public MeResponse me(@AuthenticationPrincipal HeaderUser principal) {
        return sessionService.me(principal);
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đổi mật khẩu", description = "Kiểm tra mật khẩu cũ và cập nhật mật khẩu mới cho tài khoản đang đăng nhập.")
    public void changePassword(@AuthenticationPrincipal HeaderUser principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        sessionService.changePassword(Long.valueOf(principal.getUserId()), request);
    }
}