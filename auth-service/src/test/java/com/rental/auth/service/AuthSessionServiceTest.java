package com.rental.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rental.auth.dto.ChangePasswordRequest;
import com.rental.auth.dto.LoginRequest;
import com.rental.auth.dto.LoginResponse;
import com.rental.auth.dto.MeResponse;
import com.rental.auth.dto.RefreshRequest;
import com.rental.auth.entity.Account;
import com.rental.auth.entity.AccountType;
import com.rental.auth.entity.RefreshToken;
import com.rental.auth.repository.AccountRepository;
import com.rental.auth.repository.RefreshTokenRepository;
import com.rental.auth.security.JwtProvider;
import com.rental.auth.security.RefreshTokenService;
import com.rental.common.error.BusinessException;
import com.rental.common.security.HeaderUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Unit test AuthSessionService bang Mockito, khong can DB (Task C5). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthSessionServiceTest {

    private static final String SECRET = "test-only-secret-at-least-32-chars-long-00";

    @Mock
    private AccountRepository accounts;
    @Mock
    private RefreshTokenRepository refreshTokens;
    @Mock
    private AccountRoleLookup roleLookup;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenService refreshTokenService;

    /** JwtProvider that (khong mock) de kiem tra token cap ra dung cau truc. */
    @Spy
    private JwtProvider jwtProvider = new JwtProvider(SECRET, "rental-auth", 900);

    @InjectMocks
    private AuthSessionService service;

    @Test
    void login_thanhCong_capAccessVaRefresh() {
        Account account = account(7L, "khach@mail.com", AccountType.CUSTOMER);
        when(accounts.findByUsername("khach@mail.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("matkhau123", "HASH")).thenReturn(true);
        when(roleLookup.roleCodesOf(7L)).thenReturn(List.of("CUSTOMER"));
        when(refreshTokenService.generateRawToken()).thenReturn("raw-refresh");
        when(refreshTokenService.hash("raw-refresh")).thenReturn("HASH-REFRESH");
        when(refreshTokenService.newExpiry(any())).thenReturn(Instant.now().plus(7, ChronoUnit.DAYS));
        when(accounts.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        LoginResponse res = service.login(loginRequest("khach@mail.com", "matkhau123"));

        assertThat(res.getTokenType()).isEqualTo("Bearer");
        assertThat(res.getExpiresIn()).isEqualTo(900);
        assertThat(res.getAccessToken()).isNotBlank();
        assertThat(res.getRefreshToken()).isEqualTo("raw-refresh");
        assertThat(res.getAccount().getRoles()).containsExactly("CUSTOMER");
        assertThat(account.getFailedLoginCount()).isZero();
        verify(refreshTokens).save(any(RefreshToken.class));
    }

    @Test
    void login_saiMatKhauLan1_401VaTangDem() {
        Account account = account(7L, "khach@mail.com", AccountType.CUSTOMER);
        when(accounts.findByUsername("khach@mail.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("sai", "HASH")).thenReturn(false);
        when(accounts.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.login(loginRequest("khach@mail.com", "sai")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_CREDENTIALS"));
        assertThat(account.getFailedLoginCount()).isEqualTo(1);
        assertThat(account.getLockedUntil()).isNull();
    }

    @Test
    void login_saiMatKhauLan5_423VaKhoa15Phut() {
        Account account = account(7L, "khach@mail.com", AccountType.CUSTOMER);
        account.setFailedLoginCount(4);
        when(accounts.findByUsername("khach@mail.com")).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("sai", "HASH")).thenReturn(false);
        when(accounts.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.login(loginRequest("khach@mail.com", "sai")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ACCOUNT_LOCKED"))
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatusValue()).isEqualTo(423));
        assertThat(account.getLockedUntil()).isNotNull();
        assertThat(account.getLockedUntil()).isAfter(Instant.now().plus(14, ChronoUnit.MINUTES));
    }

    @Test
    void login_taiKhoanDangKhoa_423() {
        Account account = account(7L, "khach@mail.com", AccountType.CUSTOMER);
        account.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
        when(accounts.findByUsername("khach@mail.com")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.login(loginRequest("khach@mail.com", "matkhau123")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ACCOUNT_LOCKED"));
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void login_taiKhoanBiKhoa_403VaThuHoiToken() {
        Account account = account(7L, "khach@mail.com", AccountType.CUSTOMER);
        account.setActive(false);
        when(accounts.findByUsername("khach@mail.com")).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.login(loginRequest("khach@mail.com", "matkhau123")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ACCOUNT_DISABLED"))
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatusValue()).isEqualTo(403));
        verify(refreshTokens).revokeAllByAccountId(anyLong(), any());
    }

    @Test
    void login_khongTonTai_401KhongLeak() {
        when(accounts.findByUsername("khach@mail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(loginRequest("khach@mail.com", "matkhau123")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("INVALID_CREDENTIALS"));
    }

    @Test
    void refresh_rotation_capCapMoiVaThuHoiTokenCu() {
        Account account = account(7L, "khach@mail.com", AccountType.CUSTOMER);
        RefreshToken old = refreshToken(7L, "HASH-OLD", null);
        when(refreshTokenService.hash("raw-old")).thenReturn("HASH-OLD");
        when(refreshTokens.findByTokenHash("HASH-OLD")).thenReturn(Optional.of(old));
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(roleLookup.roleCodesOf(7L)).thenReturn(List.of("CUSTOMER"));
        when(refreshTokenService.generateRawToken()).thenReturn("raw-new");
        when(refreshTokenService.hash("raw-new")).thenReturn("HASH-NEW");
        when(refreshTokenService.newExpiry(any())).thenReturn(Instant.now().plus(7, ChronoUnit.DAYS));

        LoginResponse res = service.refresh(refreshRequest("raw-old"));

        assertThat(res.getRefreshToken()).isEqualTo("raw-new");
        assertThat(old.getRevokedAt()).isNotNull();
    }

    @Test
    void refresh_dungLaiTokenDaThuHoi_401VaThuHoiToanBoPhien() {
        RefreshToken used = refreshToken(7L, "HASH-OLD", Instant.now().minus(1, ChronoUnit.HOURS));
        when(refreshTokenService.hash("raw-old")).thenReturn("HASH-OLD");
        when(refreshTokens.findByTokenHash("HASH-OLD")).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> service.refresh(refreshRequest("raw-old"))).isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("REFRESH_TOKEN_INVALID"));
        verify(refreshTokens).revokeAllByAccountId(eq(7L), any());
    }

    @Test
    void refresh_tokenHetHan_401() {
        RefreshToken expired = refreshToken(7L, "HASH-OLD", null);
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(refreshTokenService.hash("raw-old")).thenReturn("HASH-OLD");
        when(refreshTokens.findByTokenHash("HASH-OLD")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.refresh(refreshRequest("raw-old"))).isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("REFRESH_TOKEN_INVALID"));
        verify(refreshTokens, never()).revokeAllByAccountId(anyLong(), any());
    }

    @Test
    void logout_thuHoiToken_vaIdempotent() {
        RefreshToken token = refreshToken(7L, "HASH-1", null);
        when(refreshTokenService.hash("raw-1")).thenReturn("HASH-1");
        when(refreshTokens.findByTokenHash("HASH-1")).thenReturn(Optional.of(token));

        service.logout(refreshRequest("raw-1"));
        assertThat(token.getRevokedAt()).isNotNull();

        // Token da thu hoi: khong nem loi, van 204.
        service.logout(refreshRequest("raw-1"));
    }

    @Test
    void logout_tokenKhongTonTai_khongNemLoi() {
        when(refreshTokenService.hash("raw-x")).thenReturn("HASH-X");
        when(refreshTokens.findByTokenHash("HASH-X")).thenReturn(Optional.empty());

        service.logout(refreshRequest("raw-x"));
    }

    @Test
    void me_docHeaderUserVaUsernameTuDB() {
        when(accounts.findById(7L)).thenReturn(Optional.of(account(7L, "khach@mail.com", AccountType.CUSTOMER)));

        MeResponse res = service.me(new HeaderUser("7", "CUSTOMER", List.of("CUSTOMER")));

        assertThat(res.getAccountId()).isEqualTo(7L);
        assertThat(res.getUsername()).isEqualTo("khach@mail.com");
        assertThat(res.getRoles()).containsExactly("CUSTOMER");
    }

    @Test
    void doiMatKhau_saiMatKhauCu_400() {
        when(accounts.findById(7L)).thenReturn(Optional.of(account(7L, "khach@mail.com", AccountType.CUSTOMER)));
        when(passwordEncoder.matches("sai", "HASH")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(7L, changeRequest("sai", "matkhaunew")))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("WRONG_OLD_PASSWORD"));
        verify(refreshTokens, never()).revokeAllByAccountId(anyLong(), any());
    }

    @Test
    void doiMatKhau_thanhCong_thuHoiMoiToken() {
        Account account = account(7L, "khach@mail.com", AccountType.CUSTOMER);
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(passwordEncoder.matches("matkhau123", "HASH")).thenReturn(true);
        when(passwordEncoder.encode("matkhaunew")).thenReturn("HASH-NEW");
        when(accounts.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        service.changePassword(7L, changeRequest("matkhau123", "matkhaunew"));

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("HASH-NEW");
        verify(refreshTokens).revokeAllByAccountId(eq(7L), any());
    }

    private static LoginRequest loginRequest(String username, String password) {
        LoginRequest req = new LoginRequest();
        req.setUsername(username);
        req.setPassword(password);
        return req;
    }

    private static RefreshRequest refreshRequest(String rawToken) {
        RefreshRequest req = new RefreshRequest();
        req.setRefreshToken(rawToken);
        return req;
    }

    private static ChangePasswordRequest changeRequest(String oldPassword, String newPassword) {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setOldPassword(oldPassword);
        req.setNewPassword(newPassword);
        return req;
    }

    private static Account account(Long id, String username, AccountType type) {
        Account account = new Account();
        account.setId(id);
        account.setUsername(username);
        account.setPasswordHash("HASH");
        account.setAccountType(type);
        account.setActive(true);
        return account;
    }

    private static RefreshToken refreshToken(Long accountId, String hash, Instant revokedAt) {
        RefreshToken token = new RefreshToken();
        token.setId(1L);
        token.setAccountId(accountId);
        token.setTokenHash(hash);
        token.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        token.setRevokedAt(revokedAt);
        return token;
    }
}