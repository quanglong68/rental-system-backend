package com.rental.auth.entity;

/**
 * Loai tai khoan (docs muc 7.1): CUSTOMER tu dang ky; STAFF/ADMIN do ADMIN tao.
 * Luu DB dang STRING (account_type).
 */
public enum AccountType {
    CUSTOMER,
    STAFF,
    ADMIN
}
