package com.rental.auth.controller;

import com.rental.auth.dto.AccountResponse;
import com.rental.auth.dto.CreateAccountRequest;
import com.rental.auth.dto.PageResponse;
import com.rental.auth.dto.UpdateAccountRolesRequest;
import com.rental.auth.dto.UpdateAccountStatusRequest;
import com.rental.auth.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Quan tri tai khoan, chi ADMIN (docs Table 7 - Task C4).
 * Header X-User-* do Gateway gan da duoc filter chuyen thanh ROLE_*.
 */
@RestController
@RequestMapping("/api/v1/auth/accounts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AccountService accountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.createAccount(request);
    }

    @GetMapping
    public PageResponse<AccountResponse> list(@RequestParam(required = false) String accountType,
            @RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return accountService.listAccounts(accountType, active, page, size);
    }

    @PutMapping("/{id}/status")
    public AccountResponse updateStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateAccountStatusRequest request) {
        return accountService.updateStatus(id, request.getActive());
    }

    @PutMapping("/{id}/roles")
    public AccountResponse updateRoles(@PathVariable Long id,
            @Valid @RequestBody UpdateAccountRolesRequest request) {
        return accountService.updateRoles(id, request.getRoles());
    }
}
