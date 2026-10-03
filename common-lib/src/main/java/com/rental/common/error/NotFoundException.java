package com.rental.common.error;

import org.springframework.http.HttpStatus;

/**
 * Khong tim thay tai nguyen -&gt; 404 NOT_FOUND (docs Table 8).
 * Cho phep code rieng (vd PRICE_NOT_FOUND) nhung giu status 404.
 */
public class NotFoundException extends BusinessException {

    public NotFoundException(String message) {
        super(ErrorCode.NOT_FOUND, message);
    }

    public NotFoundException(String code, String message) {
        super(code, message, HttpStatus.NOT_FOUND);
    }
}
