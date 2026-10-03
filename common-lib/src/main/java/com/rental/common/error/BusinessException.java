package com.rental.common.error;

import java.util.List;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Exception nghiep vu chung. Service chi can throw exception nay (hoac lop con),
 * {@link GlobalExceptionHandler} se boc thanh {@link ErrorResponse} dung format (docs muc 8).
 * Khong nuot loi, khong tra du lieu gia (docs muc 5.2).
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus status;
    private final List<FieldViolation> details;

    public BusinessException(String code, String message, HttpStatus status) {
        this(code, message, status, null);
    }

    public BusinessException(String code, String message, HttpStatus status, List<FieldViolation> details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode.name(), message, errorCode.getHttpStatus(), null);
    }

    public BusinessException(ErrorCode errorCode, String message, List<FieldViolation> details) {
        this(errorCode.name(), message, errorCode.getHttpStatus(), details);
    }

    public int getStatusValue() {
        return status.value();
    }
}
