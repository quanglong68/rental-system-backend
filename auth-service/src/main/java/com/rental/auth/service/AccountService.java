package com.rental.auth.service;

import com.rental.auth.dto.AccountResponse;
import com.rental.auth.dto.CreateAccountRequest;
import com.rental.auth.dto.PageResponse;
import com.rental.auth.dto.RegisterRequest;
import com.rental.auth.dto.RegisterResponse;
import com.rental.auth.entity.Account;
import com.rental.auth.entity.AccountRole;
import com.rental.auth.entity.AccountType;
import com.rental.auth.entity.Role;
import com.rental.auth.outbox.OutboxEventPublisher;
import com.rental.auth.repository.AccountRepository;
import com.rental.auth.repository.AccountRoleRepository;
import com.rental.auth.repository.RoleRepository;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nghiep vu tai khoan (docs muc 7.1, Table 7 - Task C4).
 * username chuan hoa trim + lowercase; phai la email hoac SDT VN va trung voi
 * email/phone khai bao. Mat khau BCrypt cost 10 (bean PasswordEncoder trong SecurityConfig).
 * fullName/email/phone KHONG luu auth_db (thuoc User Service - docs muc 9.1);
 * Task C6 se forward qua outbox event AccountRegistered.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE_VN = Pattern.compile("^(0|\\+84)(3|5|7|8|9)\\d{8}$");

    /** Role duoc phep gan cho STAFF; ADMIN/CUSTOMER gan cho STAFF thi 422. */
    private static final Set<String> STAFF_ROLES = Set.of("QUAN_LY", "KY_THUAT", "SALE");

    private static final int MAX_PAGE_SIZE = 100;

    private final AccountRepository accounts;
    private final RoleRepository roles;
    private final AccountRoleRepository accountRoles;
    private final AccountRoleLookup roleLookup;
    private final AuthSessionService sessions;
    private final OutboxEventPublisher outboxPublisher;
    private final PasswordEncoder passwordEncoder;

    /** Dang ky CUSTOMER (PUBLIC) -> 201. Mac dinh gan role CUSTOMER. */
    @Transactional
    public RegisterResponse register(RegisterRequest req) {
        String username = normalize(req.getUsername());
        validateUsername(username, req.getEmail(), req.getPhone());
        checkUsernameAvailable(username);
        Account saved = saveAccount(username, req.getPassword(), AccountType.CUSTOMER);
        assignRoles(saved.getId(), List.of("CUSTOMER"));
        // Ghi outbox cung giao dich tao account (docs muc 5.3, Task C6).
        outboxPublisher.publishAccountRegistered(saved.getId(), saved.getAccountType(), saved.getUsername(),
                req.getFullName(), req.getEmail(), req.getPhone());
        return new RegisterResponse(saved.getId(), saved.getUsername(), saved.getAccountType());
    }

    /** ADMIN tao STAFF/ADMIN -> 201. ADMIN duoc auto-gan role ADMIN, input roles bi bo qua. */
    @Transactional
    public AccountResponse createAccount(CreateAccountRequest req) {
        String username = normalize(req.getUsername());
        validateUsername(username, req.getEmail(), req.getPhone());
        checkUsernameAvailable(username);
        AccountType type = parseAccountType(req.getAccountType());
        List<String> roleCodes = type == AccountType.ADMIN ? List.of("ADMIN") : validateStaffRoles(req.getRoles());
        Account saved = saveAccount(username, req.getPassword(), type);
        assignRoles(saved.getId(), roleCodes);
        // Ghi outbox cung giao dich tao account (docs muc 5.3, Task C6).
        outboxPublisher.publishAccountRegistered(saved.getId(), saved.getAccountType(), saved.getUsername(),
                req.getFullName(), req.getEmail(), req.getPhone());
        return toResponse(saved, roleCodes);
    }

    /** Liet ke account (ADMIN): loc theo accountType?/active?, page 0-based, size mac dinh 20/max 100. */
    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> listAccounts(String accountType, Boolean active, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("id").descending());
        Page<Account> result;
        if (accountType != null && active != null) {
            result = accounts.findByAccountTypeAndActive(parseListAccountType(accountType), active, pageable);
        } else if (accountType != null) {
            result = accounts.findByAccountType(parseListAccountType(accountType), pageable);
        } else if (active != null) {
            result = accounts.findByActive(active, pageable);
        } else {
            result = accounts.findAll(pageable);
        }
        return PageResponse.of(result.map(account -> toResponse(account, roleCodesOf(account.getId()))));
    }

    /** Khoa/mo khoa tai khoan (ADMIN) -> 200. Khoa tai khoan se thu hoi moi refresh token. */
    @Transactional
    public AccountResponse updateStatus(Long id, boolean active) {
        Account account = accounts.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Khong tim thay account"));
        account.setActive(active);
        AccountResponse response = toResponse(accounts.save(account), roleCodesOf(id));
        if (!active) {
            // Khoa tai khoan -> moi phien dang nhap deu phai hieu luc (docs muc 7.1).
            sessions.revokeAll(id);
        }
        return response;
    }

    /** Doi role cho STAFF (ADMIN) -> 200. Account khong phai STAFF thi 422. */
    @Transactional
    public AccountResponse updateRoles(Long id, List<String> roleCodes) {
        Account account = accounts.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Khong tim thay account"));
        if (account.getAccountType() != AccountType.STAFF) {
            throw new BusinessException(ErrorCode.ROLE_NOT_ALLOWED, "Chi doi role cho STAFF");
        }
        List<String> validated = validateStaffRoles(roleCodes);
        accountRoles.deleteByAccountId(id);
        assignRoles(id, validated);
        return toResponse(account, validated);
    }

    private Account saveAccount(String username, String rawPassword, AccountType type) {
        Account account = new Account();
        account.setUsername(username);
        account.setPasswordHash(passwordEncoder.encode(rawPassword));
        account.setAccountType(type);
        account.setActive(true);
        return accounts.save(account);
    }

    private void assignRoles(Long accountId, List<String> roleCodes) {
        for (String code : new LinkedHashSet<>(roleCodes)) {
            Role role = roles.findByCode(code)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "Thieu role " + code + " (seed V2)"));
            AccountRole link = new AccountRole();
            link.setAccountId(accountId);
            link.setRoleId(role.getId());
            accountRoles.save(link);
        }
    }

    private List<String> roleCodesOf(Long accountId) {
        return roleLookup.roleCodesOf(accountId);
    }

    private AccountResponse toResponse(Account account, List<String> roleCodes) {
        return new AccountResponse(account.getId(), account.getUsername(), account.getAccountType(),
                account.isActive(), List.copyOf(roleCodes));
    }

    private void checkUsernameAvailable(String username) {
        if (accounts.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS, "Username da ton tai");
        }
    }

    private List<String> validateStaffRoles(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "STAFF phai co it nhat 1 role");
        }
        for (String code : roleCodes) {
            if (!roles.existsByCode(code)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Role khong ton tai: " + code);
            }
            if (!STAFF_ROLES.contains(code)) {
                throw new BusinessException(ErrorCode.ROLE_NOT_ALLOWED, "Role khong duoc phep cho STAFF: " + code);
            }
        }
        return List.copyOf(new LinkedHashSet<>(roleCodes));
    }

    private AccountType parseAccountType(String raw) {
        AccountType type = parseAnyAccountType(raw, "accountType chi nhan STAFF|ADMIN");
        if (type == AccountType.CUSTOMER) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "accountType chi nhan STAFF|ADMIN");
        }
        return type;
    }

    /** Loc khi liet ke chap nhan ca 3 loai (ADMIN duoc xem CUSTOMER). */
    private AccountType parseListAccountType(String raw) {
        return parseAnyAccountType(raw, "accountType chi nhan CUSTOMER|STAFF|ADMIN");
    }

    private AccountType parseAnyAccountType(String raw, String message) {
        if (raw == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        try {
            return AccountType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException notEnum) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private void validateUsername(String username, String email, String phone) {
        if (!EMAIL.matcher(username).matches() && !PHONE_VN.matcher(username).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Username phai la email hoac so dien thoai");
        }
        if (email != null && !email.isBlank() && !username.equals(email.trim().toLowerCase())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Username phai trung email khai bao");
        }
        if (phone != null && !phone.isBlank() && !username.equals(phone.trim())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Username phai trung phone khai bao");
        }
    }

    private static String normalize(String raw) {
        return raw == null ? null : raw.trim().toLowerCase();
    }
}
