package com.rental.common.feign;

import com.rental.common.constant.Headers;
import com.rental.common.constant.UserTypes;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Sao chep header dinh danh sang cuoc goi Feign (docs muc 5.2).
 * Request tu nguoi dung: copy X-User-Id, X-User-Type, X-User-Roles, X-Correlation-Id
 * tu request dang xu ly. Cuoc goi do scheduler kich hoat (khong co request): gui
 * X-User-Type SYSTEM va tu sinh X-Correlation-Id.
 * Khong ghi de header da co san tren template.
 */
public class FeignHeaderInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            copyIfPresent(template, Headers.X_USER_ID, request.getHeader(Headers.X_USER_ID));
            copyIfPresent(template, Headers.X_USER_TYPE, request.getHeader(Headers.X_USER_TYPE));
            copyIfPresent(template, Headers.X_USER_ROLES, request.getHeader(Headers.X_USER_ROLES));
            String correlationId = request.getHeader(Headers.X_CORRELATION_ID);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }
            template.header(Headers.X_CORRELATION_ID, correlationId);
        } else {
            if (!template.headers().containsKey(Headers.X_USER_TYPE)) {
                template.header(Headers.X_USER_TYPE, UserTypes.SYSTEM);
            }
            if (!template.headers().containsKey(Headers.X_CORRELATION_ID)) {
                template.header(Headers.X_CORRELATION_ID, UUID.randomUUID().toString());
            }
        }
    }

    private static void copyIfPresent(RequestTemplate template, String name, String value) {
        if (value != null && !value.isBlank()) {
            template.header(name, value);
        }
    }
}
