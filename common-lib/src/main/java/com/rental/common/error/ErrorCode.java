package com.rental.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Ma loi dung chung (docs muc 8, Table 8 + Table 7 auth-service).
 * Service co ma loi nghiep vu rieng (vd ROOM_CAPACITY_EXCEEDED) thi truyen thang String code
 * vao {@link BusinessException}, khong can them vao enum nay.
 */
@Getter
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    WRONG_OLD_PASSWORD(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    ACCOUNT_LOCKED(HttpStatus.LOCKED),
    USERNAME_EXISTS(HttpStatus.CONFLICT),
    ROLE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY),
    DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }
}
