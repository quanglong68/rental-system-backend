package com.rental.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.rental.common.constant.Headers;
import feign.Request;
import feign.RetryableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.lang.reflect.Method;
import java.net.SocketTimeoutException;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class GlobalExceptionHandlerTest {

    private static final String CORRELATION_ID = "test-correlation-id";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void setUpRequestContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(Headers.X_CORRELATION_ID, CORRELATION_ID);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void notFound_keepsCustomCodeAnd404_withCorrelationTraceId() {
        ResponseEntity<ErrorResponse> response = handler.handleBusiness(new NotFoundException("PRICE_NOT_FOUND",
                "Khong tim thay gia"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("PRICE_NOT_FOUND");
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getTraceId()).isEqualTo(CORRELATION_ID);
    }

    @Test
    void businessException_customServiceCode_keepsStatus() {
        ResponseEntity<ErrorResponse> response = handler
                .handleBusiness(new BusinessException("ROOM_CAPACITY_EXCEEDED", "Phong da du",
                        HttpStatus.UNPROCESSABLE_ENTITY));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getCode()).isEqualTo("ROOM_CAPACITY_EXCEEDED");
        assertThat(response.getBody().getStatus()).isEqualTo(422);
    }

    @Test
    void dependencyUnavailable_mapsTo503() {
        ResponseEntity<ErrorResponse> response = handler
                .handleBusiness(new DependencyUnavailableException("Property khong phan hoi"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getCode()).isEqualTo("DEPENDENCY_UNAVAILABLE");
    }

    @Test
    void validation_mapsTo400WithFieldDetails() throws Exception {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "registerRequest");
        bindingResult.addError(new FieldError("registerRequest", "password", "Mat khau toi thieu 8 ky tu"));
        Method method = DummyController.class.getMethod("dummy", Object.class);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                new org.springframework.core.MethodParameter(method, 0), bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().getDetails()).hasSize(1);
        assertThat(response.getBody().getDetails().get(0).field()).isEqualTo("password");
    }

    @Test
    void typeMismatch_mapsTo400() {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException("abc", Integer.class,
                "page", null, new IllegalArgumentException());

        ResponseEntity<ErrorResponse> response = handler.handleBadRequest(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void accessDenied_mapsTo403() {
        ResponseEntity<ErrorResponse> response = handler.handleForbidden(new AccessDeniedException("denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().getCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    void unexpected_mapsTo500WithoutLeakingMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(new RuntimeException("select * from secret"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().getMessage()).doesNotContain("secret");
    }

    @Test
    void missingRequestContext_generatesTraceId() {
        RequestContextHolder.resetRequestAttributes();

        ResponseEntity<ErrorResponse> response = handler.handleBusiness(new NotFoundException("Khong thay"));

        assertThat(response.getBody().getTraceId()).isNotBlank();
    }

    @Test
    void feignTimeout_mapsTo503() {
        feign.Request request = org.mockito.Mockito.mock(feign.Request.class);
        org.mockito.Mockito.when(request.httpMethod()).thenReturn(feign.Request.HttpMethod.GET);
        RetryableException timeout = new RetryableException(-1, "read timed out", Request.HttpMethod.GET,
                new SocketTimeoutException(), (Date) null, request);

        ResponseEntity<ErrorResponse> response = handler.handleFeign(timeout);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getCode()).isEqualTo("DEPENDENCY_UNAVAILABLE");
    }

    @Test
    void circuitOpen_mapsTo503() {
        CallNotPermittedException open = CallNotPermittedException
                .createCallNotPermittedException(CircuitBreaker.ofDefaults("test"));

        ResponseEntity<ErrorResponse> response = handler.handleCircuitOpen(open);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().getCode()).isEqualTo("DEPENDENCY_UNAVAILABLE");
    }

    @SuppressWarnings("unused")
    static class DummyController {
        public void dummy(Object body) {
        }
    }
}
