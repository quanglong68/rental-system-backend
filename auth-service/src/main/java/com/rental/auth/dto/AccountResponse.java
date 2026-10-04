package com.rental.auth.dto;

import com.rental.auth.entity.AccountType;
import java.util.List;
import lombok.Value;

/**
 * Thong tin account dung cho cac API ADMIN (POST/GET /accounts, PUT status/roles).
 */
@Value
public class AccountResponse {

    Long accountId;
    String username;
    AccountType accountType;
    boolean active;
    List<String> roles;
}
