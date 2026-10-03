package com.rental.common.error;

import com.rental.common.constant.Headers;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Boc moi exception thanh {@link ErrorResponse} dung format (docs muc 8, Table 8).
 * traceId lay tu header X-Correlation-Id Gateway chuyen vao; thieu thi sinh UUID.
 * Moi service import handler nay: {@code @Import(GlobalExceptionHandler.class)}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Loi nghiep vu do service throw chu dong (gi nguyen code cua ben kia khi goi Feign). */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        return build(ex.getCode(), ex.getMessage(), ex.getStatus(), ex.getDetails());
    }

    /** @Valid tren @RequestBody loi -> 400 VALIDATION_ERROR kem chi tiet tung field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldViolation> details = ex.getBindingResult().getAllErrors().stream()
                .map(err -> err instanceof FieldError fieldError
                        ? new FieldViolation(fieldError.getField(), messageOf(fieldError))
                        : new FieldViolation(err.getObjectName(), messageOf(err)))
                .collect(Collectors.toList());
        return build(ErrorCode.VALIDATION_ERROR.name(), "Du lieu vao sai", ErrorCode.VALIDATION_ERROR.getHttpStatus(),
                details);
    }

    /** @Validated tren @RequestParam/@PathVariable loi -> 400 VALIDATION_ERROR. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraint(ConstraintViolationException ex) {
        List<FieldViolation> details = ex.getConstraintViolations().stream()
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .collect(Collectors.toList());
        return build(ErrorCode.VALIDATION_ERROR.name(), "Du lieu vao sai", ErrorCode.VALIDATION_ERROR.getHttpStatus(),
                details);
    }

    /** Thieu/sai kieu param, body JSON sai dinh dang -> 400 VALIDATION_ERROR. */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        return build(ErrorCode.VALIDATION_ERROR.name(), "Du lieu vao sai",
                ErrorCode.VALIDATION_ERROR.getHttpStatus(), null);
    }

    /** Khong du quyen (@PreAuthorize) -> 403 FORBIDDEN (docs Table 8). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(AccessDeniedException ex) {
        return build(ErrorCode.FORBIDDEN.name(), "Khong du quyen", ErrorCode.FORBIDDEN.getHttpStatus(), null);
    }

    /** Chua dang nhap / thieu Authentication trong service -> 401 UNAUTHENTICATED. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleUnauthenticated(AuthenticationException ex) {
        return build(ErrorCode.UNAUTHENTICATED.name(), "Chua dang nhap", ErrorCode.UNAUTHENTICATED.getHttpStatus(),
                null);
    }

    /** Loi khong luong truoc -> 500 INTERNAL_ERROR, khong lo thong tin noi bo. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        return build(ErrorCode.INTERNAL_ERROR.name(), "Loi he thong, vui long thu lai sau",
                ErrorCode.INTERNAL_ERROR.getHttpStatus(), null);
    }

    private ResponseEntity<ErrorResponse> build(String code, String message, HttpStatus status,
            List<FieldViolation> details) {
        ErrorResponse body = ErrorResponse.builder()
                .code(code)
                .message(message)
                .status(status.value())
                .traceId(resolveTraceId())
                .details(details)
                .build();
        return ResponseEntity.status(status).body(body);
    }

    private String resolveTraceId() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                String correlationId = attrs.getRequest().getHeader(Headers.X_CORRELATION_ID);
                if (correlationId != null && !correlationId.isBlank()) {
                    return correlationId;
                }
            }
        } catch (Exception ignored) {
            // Khong de traceId lam vo response loi.
        }
        return UUID.randomUUID().toString();
    }

    private static String messageOf(org.springframework.validation.ObjectError err) {
        return err.getDefaultMessage() != null ? err.getDefaultMessage() : "Gia tri khong hop le";
    }
}
