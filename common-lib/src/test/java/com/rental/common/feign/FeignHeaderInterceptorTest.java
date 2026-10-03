package com.rental.common.feign;

import static org.assertj.core.api.Assertions.assertThat;

import com.rental.common.constant.Headers;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class FeignHeaderInterceptorTest {

    private final FeignHeaderInterceptor interceptor = new FeignHeaderInterceptor();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void coRequest_saoChepDu4Header() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(Headers.X_USER_ID, "1024");
        request.addHeader(Headers.X_USER_TYPE, "STAFF");
        request.addHeader(Headers.X_USER_ROLES, "QUAN_LY,SALE");
        request.addHeader(Headers.X_CORRELATION_ID, "corr-1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(first(template, Headers.X_USER_ID)).isEqualTo("1024");
        assertThat(first(template, Headers.X_USER_TYPE)).isEqualTo("STAFF");
        assertThat(first(template, Headers.X_USER_ROLES)).isEqualTo("QUAN_LY,SALE");
        assertThat(first(template, Headers.X_CORRELATION_ID)).isEqualTo("corr-1");
    }

    @Test
    void scheduler_khongRequest_guiSystemVaSinhCorrelationId() {
        RequestContextHolder.resetRequestAttributes();
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(first(template, Headers.X_USER_TYPE)).isEqualTo("SYSTEM");
        assertThat(first(template, Headers.X_CORRELATION_ID)).isNotBlank();
        assertThat(template.headers()).doesNotContainKey(Headers.X_USER_ID);
    }

    @Test
    void khongGhiDeHeaderDaCoSan() {
        RequestContextHolder.resetRequestAttributes();
        RequestTemplate template = new RequestTemplate();
        template.header(Headers.X_USER_TYPE, "CUSTOM");

        interceptor.apply(template);

        assertThat(first(template, Headers.X_USER_TYPE)).isEqualTo("CUSTOM");
    }

    private static String first(RequestTemplate template, String name) {
        return template.headers().getOrDefault(name, java.util.List.of()).stream().findFirst().orElse(null);
    }
}
