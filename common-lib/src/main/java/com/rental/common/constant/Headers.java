package com.rental.common.constant;

/**
 * Ten header chuan dung chung toan he thong (docs muc 6, Table 6).
 * Gateway gan 3 header X-User-* sau khi verify JWT; service chi doc, khong verify JWT.
 * Client tu gui cac header nay se bi Gateway xoa truoc khi xu ly (chong gia mao).
 */
public final class Headers {

    private Headers() {
    }

    /** Claim sub (account id). Vi du: 1024 */
    public static final String X_USER_ID = "X-User-Id";

    /** Claim typ (CUSTOMER, STAFF, ADMIN; SYSTEM khi scheduler goi noi bo). */
    public static final String X_USER_TYPE = "X-User-Type";

    /** Claim roles noi bang dau phay. Vi du: QUAN_LY,SALE */
    public static final String X_USER_ROLES = "X-User-Roles";

    /** Gateway sinh UUID neu request thieu, de truy vet log xuyen service. */
    public static final String X_CORRELATION_ID = "X-Correlation-Id";
}
