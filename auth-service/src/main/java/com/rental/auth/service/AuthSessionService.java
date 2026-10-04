package com.rental.auth.service;

import com.rental.auth.dto.ChangePasswordRequest;
import com.rental.auth.dto.LoginRequest;
import com.rental.auth.dto.LoginResponse;
import com.rental.auth.dto.MeResponse;
import com.rental.auth.dto.RefreshRequest;
import com.rental.auth.entity.Account;
import com.rental.auth.entity.RefreshToken;
import com.rental.auth.repository.AccountRepository;
import com.rental.auth.repository.RefreshTokenRepository;
import com.rental.auth.security.JwtProvider;
import com.rental.auth.security.RefreshTokenService;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.security.HeaderUser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quan ly phien dang nhap (docs muc 7.1, Table 7 - Task C5).
 * 1. Sai mat khau 5 lan lien tiep -> khoa 15 phut, tra 423 ACCOUNT_LOCKED (tu lan thu 5).
 * 2. Tai khoan bi khoa (isActive=false) -> 403 ACCOUNT_DISABLED + thu hoi moi refresh token.
 * 3. Refresh rotation: cap cap moi, danh dau revoked_at token cu; dung lai token da thu hoi
 *    -> thu hoi TOAN BO phien cua account (chong replay) + 401 REFRESH_TOKEN_INVALID.
 * 4. Doi mat khau / khoa tai khoan -> thu hoi moi refresh token.
 * 5. GET /me doc principal tu header Gateway (khong verify JWT lai).
 */
@Service
@RequiredArgsConstructor
public class AuthSessionService {

    /** So lan sai truoc khi khoa (docs muc 7.1). */
    public static final int MAX_FAILED_LOGINS = 5;

    /** Thoi gian khoa tai khoan (docs muc 7.1: 15 phut). */
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final AccountRepository accounts;
    private final RefreshTokenRepository refreshTokens;
    private final AccountRoleLookup roleLookup;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    /** Dang nhap -> cap cap access + refresh (role that tu DB). */
    // noRollbackFor: moi lan throw o day deu di kem ghi nhan trang thai phai giu lai
    // (dem sai mat khau, locked_until, thu hoi token khi tai khoan bi khoa).
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse login(LoginRequest request) {
        String username = normalize(request.getUsername());
        Account account = accounts.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Sai thong tin dang nhap"));
        if (!account.isActive()) {
            revokeAll(account.getId());
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "Tai khoan da bi khoa");
        }
        Instant now = Instant.now();
        if (account.getLockedUntil() != null && account.getLockedUntil().isAfter(now)) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "Tai khoan dang bi khoa do sai mat khau");
        }
        if (!passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw handleWrongPassword(account, now);
        }
        account.setFailedLoginCount(0);
        account.setLockedUntil(null);
        accounts.save(account);
        return issueSession(account);
    }

    /** Refresh token -> cap cap moi (rotation), token cu bi thu hoi ngay. */
    // noRollbackFor: truong hop replay phai giu lai revokeAll truoc khi nem 401.
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginResponse refresh(RefreshRequest request) {
        String hash = refreshTokenService.hash(request.getRefreshToken());
        RefreshToken token = refreshTokens.findByTokenHash(hash).orElseThrow(
                () -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token khong hop le"));
        if (token.getRevokedAt() != null) {
            // Dung lai token da thu hoi = replay: thu hoi toan bo phien de chong attacker.
            revokeAll(token.getAccountId());
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token da bi thu hoi");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token da het han");
        }
        Account account = accounts.findById(token.getAccountId()).orElseThrow(
                () -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID, "Tai khoan khong ton tai"));
        if (!account.isActive()) {
            revokeAll(account.getId());
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED, "Tai khoan da bi khoa");
        }
        token.setRevokedAt(Instant.now());
        refreshTokens.save(token);
        return issueSession(account);
    }

    /** Logout: thu hoi token cua phien. Token sai/het hieu van tra 204 (idempotent). */
    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokens.findByTokenHash(refreshTokenService.hash(request.getRefreshToken()))
                .filter(token -> token.getRevokedAt() == null).ifPresent(token -> {
                    token.setRevokedAt(Instant.now());
                    refreshTokens.save(token);
                });
    }

    /** GET /me: id/roles tu header Gateway, username tra tu DB. */
    @Transactional(readOnly = true)
    public MeResponse me(HeaderUser principal) {
        Long accountId = Long.valueOf(principal.getUserId());
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Khong tim thay account"));
        return new MeResponse(account.getId(), account.getUsername(), account.getAccountType(),
                principal.getRoles());
    }

    /** Doi mat khau: sai mat khau cu -> 400; thanh cong -> thu hoi moi refresh token. */
    @Transactional
    public void changePassword(Long accountId, ChangePasswordRequest request) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Khong tim thay account"));
        if (!passwordEncoder.matches(request.getOldPassword(), account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.WRONG_OLD_PASSWORD, "Mat khau cu khong dung");
        }
        account.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        account.setFailedLoginCount(0);
        account.setLockedUntil(null);
        accounts.save(account);
        revokeAll(accountId);
    }

    /** Thu hoi moi refresh token cua tai khoan (goi khi khoa tai khoan/doi mat khau/replay). */
    @Transactional
    public void revokeAll(Long accountId) {
        refreshTokens.revokeAllByAccountId(accountId, Instant.now());
    }

    private BusinessException handleWrongPassword(Account account, Instant now) {
        int failed = account.getFailedLoginCount() + 1;
        account.setFailedLoginCount(failed);
        if (failed >= MAX_FAILED_LOGINS) {
            account.setLockedUntil(now.plus(LOCK_DURATION));
            accounts.save(account);
            return new BusinessException(ErrorCode.ACCOUNT_LOCKED,
                    "Sai mat khau " + MAX_FAILED_LOGINS + " lan lien tiep, tai khoan bi khoa 15 phut");
        }
        accounts.save(account);
        return new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Sai thong tin dang nhap");
    }

    private LoginResponse issueSession(Account account) {
        List<String> roleCodes = roleLookup.roleCodesOf(account.getId());
        String accessToken = jwtProvider.generateAccessToken(account.getId(), account.getAccountType().name(),
                roleCodes);
        String rawRefresh = refreshTokenService.generateRawToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setAccountId(account.getId());
        refreshToken.setTokenHash(refreshTokenService.hash(rawRefresh));
        refreshToken.setExpiresAt(refreshTokenService.newExpiry(Instant.now()));
        refreshTokens.save(refreshToken);
        return new LoginResponse(accessToken, rawRefresh, "Bearer", jwtProvider.getAccessExpirationSeconds(),
                new LoginResponse.AccountInfo(account.getId(), account.getUsername(), account.getAccountType(),
                        roleCodes));
    }

    private static String normalize(String raw) {
        return raw == null ? null : raw.trim().toLowerCase();
    }
}