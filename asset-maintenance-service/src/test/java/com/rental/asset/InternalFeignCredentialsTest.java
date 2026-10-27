package com.rental.asset;

import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InternalFeignCredentialsTest {
    private static final String KEY = "test-only-internal-service-key-32-bytes-minimum";

    @Test
    void propertyClientAddsOnlyPropertyCredential() {
        var request = new RequestTemplate();
        new PropertyInternalFeignConfig().propertyInternalCredential(KEY).apply(request);
        assertThat(request.headers()).containsKey("X-Property-Internal-Key")
                .doesNotContainKey("X-Staff-Assignment-Key");
        assertThat(request.headers().get("X-Property-Internal-Key")).containsExactly(KEY);
    }

    @Test
    void staffClientAddsOnlyStaffCredentialAndMissingKeyAddsNothing() {
        var request = new RequestTemplate();
        new StaffAssignmentFeignConfig().staffAssignmentCredential(KEY).apply(request);
        assertThat(request.headers()).containsKey("X-Staff-Assignment-Key")
                .doesNotContainKey("X-Property-Internal-Key");
        assertThat(request.headers().get("X-Staff-Assignment-Key")).containsExactly(KEY);

        var missing = new RequestTemplate();
        new StaffAssignmentFeignConfig().staffAssignmentCredential("").apply(missing);
        new PropertyInternalFeignConfig().propertyInternalCredential("").apply(missing);
        assertThat(missing.headers()).isEmpty();
    }
}
