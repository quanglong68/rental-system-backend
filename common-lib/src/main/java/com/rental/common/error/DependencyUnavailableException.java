package com.rental.common.error;

/**
 * Service phu thuoc khong phan hoi (Feign 5xx/timeout/mach mo) -&gt; 503
 * voi code DEPENDENCY_UNAVAILABLE (docs muc 5.2, Table 8).
 */
public class DependencyUnavailableException extends BusinessException {

    public DependencyUnavailableException(String message) {
        super(ErrorCode.DEPENDENCY_UNAVAILABLE, message);
    }

    public DependencyUnavailableException(String message, Throwable cause) {
        super(ErrorCode.DEPENDENCY_UNAVAILABLE.name(), message,
                ErrorCode.DEPENDENCY_UNAVAILABLE.getHttpStatus(), null);
        initCause(cause);
    }
}
