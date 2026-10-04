package com.rental.auth.dto;

import com.rental.auth.entity.AccountType;
import lombok.Value;

/**
 * Response dang ky thanh cong (Table 7): 201 + thong tin co ban (khong bao gom role).
 */
@Value
public class RegisterResponse {

    Long accountId;
    String username;
    AccountType accountType;
}
