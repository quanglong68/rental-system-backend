package com.rental.user.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class StaffAssignmentAuthFilterTest {
    private static final String KEY = "test-only-staff-assignment-key-32-bytes-minimum";
    private final StaffAssignmentAuthFilter filter = new StaffAssignmentAuthFilter(KEY);

    @Test
    void requiresCredentialForInternalStaffLookup() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/staff/51/buildings");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { throw new AssertionError("forwarded"); });
        assertThat(response.getStatus()).isEqualTo(401);

        request = new MockHttpServletRequest("GET", "/internal/staff/51/buildings");
        request.addHeader(StaffAssignmentAuthFilter.HEADER, "wrong");
        response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> { throw new AssertionError("forwarded"); });
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void allowsValidInternalCaller() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/staff/51/buildings");
        request.addHeader(StaffAssignmentAuthFilter.HEADER, KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        final boolean[] forwarded = {false};
        FilterChain chain = (req, res) -> forwarded[0] = true;
        filter.doFilter(request, response, chain);
        assertThat(forwarded[0]).isTrue();
    }
}
