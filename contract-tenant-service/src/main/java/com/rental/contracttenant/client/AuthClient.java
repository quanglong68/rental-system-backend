package com.rental.contracttenant.client;

import com.rental.common.feign.FeignDefaultConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** Contract → Auth: re-verify the signer's password before recording an electronic signature. */
@FeignClient(name = "auth-service", contextId = "authClient", configuration = FeignDefaultConfig.class)
public interface AuthClient {

    @PostMapping("/internal/auth/verify-password")
    VerifyPasswordResponse verifyPassword(@RequestBody VerifyPasswordRequest request);

    record VerifyPasswordRequest(Long accountId, String password) {
    }

    record VerifyPasswordResponse(boolean valid, String username) {
    }
}
