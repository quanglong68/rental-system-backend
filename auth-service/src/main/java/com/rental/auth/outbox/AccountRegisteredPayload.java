package com.rental.auth.outbox;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payload event AccountRegistered (docs Table 16): User Service dung de tu tao ho so.
 * fullName/email/phone lay tu request dang ky (auth_db khong luu - docs muc 9.1),
 * co the null neu client khong gui.
 */
@Getter
@Setter
@NoArgsConstructor
public class AccountRegisteredPayload {

    private Long accountId;
    private String accountType;
    private String username;
    private String fullName;
    private String email;
    private String phone;
}