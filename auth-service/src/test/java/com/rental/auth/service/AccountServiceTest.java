package com.rental.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rental.auth.dto.AccountResponse;
import com.rental.auth.dto.CreateAccountRequest;
import com.rental.auth.dto.RegisterRequest;
import com.rental.auth.dto.RegisterResponse;
import com.rental.auth.entity.Account;
import com.rental.auth.entity.AccountRole;
import com.rental.auth.entity.AccountType;
import com.rental.auth.entity.Role;
import com.rental.auth.repository.AccountRepository;
import com.rental.auth.repository.AccountRoleRepository;
import com.rental.auth.repository.RoleRepository;
import com.rental.common.error.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Unit test AccountService bang Mockito, khong can DB (Task C4). */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accounts;
    @Mock
    private RoleRepository roles;
    @Mock
    private AccountRoleRepository accountRoles;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthSessionService sessions;
    @Mock
    private AccountRoleLookup roleLookup;
    @Mock
    private com.rental.auth.outbox.OutboxEventPublisher outboxPublisher;

    @InjectMocks
    private AccountService service;

    @Test
    void register_thanhCong_ganRoleCustomer() {
        RegisterRequest req = registerRequest("customer@mail.com", "matkhau123", "Khach Hang", "customer@mail.com",
                null);
        when(accounts.existsByUsername("customer@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("matkhau123")).thenReturn("HASH");
        when(accounts.save(any(Account.class))).thenAnswer(inv -> {
            Account saved = inv.getArgument(0);
            saved.setId(7L);
            return saved;
        });
        when(roles.findByCode("CUSTOMER")).thenReturn(Optional.of(role(5, "CUSTOMER")));

        RegisterResponse res = service.register(req);

        assertThat(res.getAccountId()).isEqualTo(7L);
        assertThat(res.getUsername()).isEqualTo("customer@mail.com");
        assertThat(res.getAccountType()).isEqualTo(AccountType.CUSTOMER);
        verify(accountRoles).save(any(AccountRole.class));
    }

    @Test
    void register_trungUsername_409() {
        RegisterRequest req = registerRequest("trung@mail.com", "matkhau123", "Trung", "trung@mail.com", null);
        when(accounts.existsByUsername("trung@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(req)).isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("USERNAME_EXISTS"))
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatusValue()).isEqualTo(409));
    }

    @Test
    void register_usernameSaiDinhDang_400() {
        RegisterRequest req = registerRequest("khong-phai-email", "matkhau123", "Sai", null, null);

        assertThatThrownBy(() -> service.register(req)).isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("VALIDATION_ERROR"));
    }

    @Test
    void register_emailKhongKhopUsername_400() {
        RegisterRequest req = registerRequest("0912345678", "matkhau123", "Sai", "khac@mail.com", null);

        assertThatThrownBy(() -> service.register(req)).isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("VALIDATION_ERROR"));
    }

    @Test
    void register_chuanHoaUsername_lowercase() {
        RegisterRequest req = registerRequest("  ADMIN@Mail.COM ", "matkhau123", "Chuan", "ADMIN@Mail.COM", null);
        when(accounts.existsByUsername("admin@mail.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("HASH");
        when(accounts.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roles.findByCode("CUSTOMER")).thenReturn(Optional.of(role(5, "CUSTOMER")));

        service.register(req);

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("admin@mail.com");
    }

    @Test
    void createStaff_thieuRole_400() {
        CreateAccountRequest req = createRequest("staff@mail.com", "STAFF", null);
        when(accounts.existsByUsername("staff@mail.com")).thenReturn(false);

        assertThatThrownBy(() -> service.createAccount(req)).isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("VALIDATION_ERROR"));
    }

    @Test
    void createStaff_roleAdmin_422() {
        CreateAccountRequest req = createRequest("staff@mail.com", "STAFF", List.of("ADMIN"));
        when(accounts.existsByUsername("staff@mail.com")).thenReturn(false);
        when(roles.existsByCode("ADMIN")).thenReturn(true);

        assertThatThrownBy(() -> service.createAccount(req)).isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ROLE_NOT_ALLOWED"))
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatusValue()).isEqualTo(422));
    }

    @Test
    void createStaff_roleKhongTonTai_400() {
        CreateAccountRequest req = createRequest("staff@mail.com", "STAFF", List.of("KHONG_CO"));
        when(accounts.existsByUsername("staff@mail.com")).thenReturn(false);
        when(roles.existsByCode("KHONG_CO")).thenReturn(false);

        assertThatThrownBy(() -> service.createAccount(req)).isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("VALIDATION_ERROR"));
    }

    @Test
    void createAdmin_autoGanRoleAdmin_boQuaInput() {
        CreateAccountRequest req = createRequest("admin@mail.com", "ADMIN", List.of("SALE"));
        when(accounts.existsByUsername("admin@mail.com")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("HASH");
        when(accounts.save(any(Account.class))).thenAnswer(inv -> {
            Account saved = inv.getArgument(0);
            saved.setId(9L);
            return saved;
        });
        when(roles.findByCode("ADMIN")).thenReturn(Optional.of(role(1, "ADMIN")));

        AccountResponse res = service.createAccount(req);

        assertThat(res.getAccountType()).isEqualTo(AccountType.ADMIN);
        assertThat(res.getRoles()).containsExactly("ADMIN");
    }

    @Test
    void updateRoles_choAdmin_422() {
        Account admin = account(9L, "admin@mail.com", AccountType.ADMIN);
        when(accounts.findById(9L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.updateRoles(9L, List.of("SALE"))).isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("ROLE_NOT_ALLOWED"));
    }

    @Test
    void updateStatus_khongTonTai_404() {
        when(accounts.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(404L, false)).isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void updateStatus_thanhCong_kemRoles() {
        Account staff = account(8L, "staff@mail.com", AccountType.STAFF);
        when(accounts.findById(8L)).thenReturn(Optional.of(staff));
        when(accounts.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(roleLookup.roleCodesOf(8L)).thenReturn(List.of("QUAN_LY"));

        AccountResponse res = service.updateStatus(8L, false);

        assertThat(res.isActive()).isFalse();
        assertThat(res.getRoles()).containsExactly("QUAN_LY");
    }

    private static RegisterRequest registerRequest(String username, String password, String fullName, String email,
            String phone) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(username);
        req.setPassword(password);
        req.setFullName(fullName);
        req.setEmail(email);
        req.setPhone(phone);
        return req;
    }

    private static CreateAccountRequest createRequest(String username, String accountType, List<String> roles) {
        CreateAccountRequest req = new CreateAccountRequest();
        req.setUsername(username);
        req.setPassword("matkhau123");
        req.setFullName("Nhan Vien");
        req.setAccountType(accountType);
        req.setRoles(roles);
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

    private static Role role(Integer id, String code) {
        Role role = new Role();
        role.setId(id);
        role.setCode(code);
        return role;
    }
}
