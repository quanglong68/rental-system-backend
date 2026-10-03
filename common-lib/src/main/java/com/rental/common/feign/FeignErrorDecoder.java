package com.rental.common.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.common.error.BusinessException;
import com.rental.common.error.DependencyUnavailableException;
import com.rental.common.error.ErrorResponse;
import com.rental.common.error.NotFoundException;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.InputStream;
import org.springframework.http.HttpStatus;

/**
 * Anh xa loi HTTP tu service bi goi (docs muc 5.2):
 * 404 -&gt; NotFoundException; 4xx khac -&gt; BusinessException giu nguyen code ben kia
 * (doc ErrorResponse chung trong body); 5xx -&gt; DependencyUnavailableException (503).
 * Khong nuot loi, khong tra du lieu gia.
 * Timeout / loi mang (RetryableException) khong qua day ma do GlobalExceptionHandler
 * chuyen thang sang 503.
 */
public class FeignErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    public FeignErrorDecoder() {
        this(new ObjectMapper());
    }

    FeignErrorDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        if (status == HttpStatus.NOT_FOUND.value()) {
            return new NotFoundException("Du lieu yeu cau khong ton tai o service phu thuoc");
        }
        if (status >= 400 && status < 500) {
            ErrorResponse upstream = readUpstreamError(response);
            HttpStatus httpStatus = HttpStatus.resolve(status);
            HttpStatus resolved = httpStatus != null ? httpStatus : HttpStatus.BAD_REQUEST;
            if (upstream != null && upstream.getCode() != null && !upstream.getCode().isBlank()) {
                return new BusinessException(upstream.getCode(), upstream.getMessage(), resolved);
            }
            String reason = response.reason();
            return new BusinessException("UPSTREAM_ERROR",
                    reason != null && !reason.isBlank() ? reason : "Yeu cau khong hop le", resolved);
        }
        return new DependencyUnavailableException(
                "Service phu thuoc khong phan hoi (" + methodKey + ": " + status + ")");
    }

    private ErrorResponse readUpstreamError(Response response) {
        if (response.body() == null) {
            return null;
        }
        try (InputStream in = response.body().asInputStream()) {
            if (in == null) {
                return null;
            }
            return objectMapper.readValue(in, ErrorResponse.class);
        } catch (Exception ignored) {
            return null;
        }
    }
}
