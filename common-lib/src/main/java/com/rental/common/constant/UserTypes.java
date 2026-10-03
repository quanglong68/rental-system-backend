package com.rental.common.constant;

/**
 * Gia tri hop le cua header {@link Headers#X_USER_TYPE} (docs muc 6, Table 6).
 * SYSTEM danh cho cuoc goi Feign do scheduler kich hoat (khong co nguoi dung).
 */
public final class UserTypes {

    private UserTypes() {
    }

    public static final String CUSTOMER = "CUSTOMER";
    public static final String STAFF = "STAFF";
    public static final String ADMIN = "ADMIN";
    public static final String SYSTEM = "SYSTEM";
}
