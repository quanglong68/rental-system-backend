package com.rental.auth.dto;

import com.rental.auth.entity.AccountType;
import java.util.List;
import lombok.Value;

/**
 * Response GET /me (docs Table 7): id/roles lay tu header Gateway da gan
 * (khong verify JWT lai - muc 7.1), username tra tu DB.
 */
@Value
public class MeResponse {

    Long accountId;
    String username;
    AccountType accountType;
    List<String> roles;
}