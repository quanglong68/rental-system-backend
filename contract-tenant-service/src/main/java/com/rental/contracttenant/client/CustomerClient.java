package com.rental.contracttenant.client;

import com.rental.common.feign.FeignDefaultConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign call #8 of the closed list (spec 5.2): Contract → User, {@code GET /internal/customers/{id}/exists},
 * used to validate customer existence before creating a manual DRAFT contract.
 *
 * <p>Response is 204 No Content if customer exists, 404 if not found.
 *
 * <p>Failure policy (spec 5.2): 404 → reject draft creation with CUSTOMER_NOT_FOUND;
 * 5xx, timeout, open circuit → reject with 503 DEPENDENCY_UNAVAILABLE. Never bypass validation.
 */
@FeignClient(name = "user-notification-service", contextId = "customerClient", configuration = FeignDefaultConfig.class)
public interface CustomerClient {

    @GetMapping("/internal/customers/{id}/exists")
    void checkExists(@PathVariable("id") Long id);
}
