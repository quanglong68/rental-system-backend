package com.rental.auth.service;

import com.rental.auth.repository.AccountRoleRepository;
import com.rental.auth.repository.RoleRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Doc danh sach role code cua mot tai khoan (account_role -> role).
 * Tach rieng de AccountService (tao/sua tai khoan) va AuthSessionService (cap token)
 * dung chung mot noi logic - tranh viet trung (AIRule §2).
 */
@Service
@RequiredArgsConstructor
public class AccountRoleLookup {

    private final AccountRoleRepository accountRoles;
    private final RoleRepository roles;

    /** Role code da sap xop de response o dinh. Rong neu tai khoan chua gan role. */
    @Transactional(readOnly = true)
    public List<String> roleCodesOf(Long accountId) {
        List<Integer> ids = new ArrayList<>();
        for (var link : accountRoles.findByAccountId(accountId)) {
            ids.add(link.getRoleId());
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        for (var role : roles.findAllById(ids)) {
            codes.add(role.getCode());
        }
        codes.sort(String::compareTo);
        return codes;
    }
}