package com.rental.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.common.constant.Headers;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class HeaderAuthenticationFilterTest {

    private final HeaderAuthenticationFilter filter = new HeaderAuthenticationFilter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void duHeader_taoAuthenticationVaTruyenTiep() throws Exception {
        MockHttpServletRequest request = request("/api/v1/contracts/1");
        request.addHeader(Headers.X_USER_ID, "1024");
        request.addHeader(Headers.X_USER_TYPE, "STAFF");
        request.addHeader(Headers.X_USER_ROLES, "QUAN_LY,SALE");
        request.addHeader(Headers.X_CORRELATION_ID, "corr-1");
        AtomicReference<String> mdcInChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> mdcInChain.set(MDC.get(HeaderAuthenticationFilter.MDC_CORRELATION_ID));

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal).isInstanceOf(HeaderUser.class);
        assertThat(((HeaderUser) principal).getUserId()).isEqualTo("1024");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_QUAN_LY", "ROLE_SALE");
        assertThat(mdcInChain.get()).isEqualTo("corr-1");
    }

    @Test
    void thieuHeader_tra401KhongTruyenTiep() throws Exception {
        MockHttpServletRequest request = request("/api/v1/contracts/1");
        request.addHeader(Headers.X_CORRELATION_ID, "corr-2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(401);
        var body = objectMapper.readValue(response.getContentAsString(),
                com.rental.common.error.ErrorResponse.class);
        assertThat(body.getCode()).isEqualTo("UNAUTHENTICATED");
        assertThat(body.getTraceId()).isEqualTo("corr-2");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void duongInternal_khongCanHeaderVanQua() throws Exception {
        MockHttpServletRequest request = request("/internal/staff/5/buildings");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void thieuRolesHeader_vanXacThucVoiQuyenRong() throws Exception {
        MockHttpServletRequest request = request("/api/v1/users/me");
        request.addHeader(Headers.X_USER_ID, "7");
        request.addHeader(Headers.X_USER_TYPE, "CUSTOMER");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).isEmpty();
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }
}
