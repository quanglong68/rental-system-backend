package com.rental.gateway.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** Shows documentation only for services currently registered in discovery. */
@RestController
public class SwaggerConfigController {

    private static final List<String> PUBLIC_API_SERVICES = List.of(
            "auth-service", "user-notification-service", "property-service",
            "listing-booking-service", "billing-utility-service", "asset-maintenance-service");

    private final DiscoveryClient discovery;

    public SwaggerConfigController(DiscoveryClient discovery) {
        this.discovery = discovery;
    }

    @GetMapping(value = "/swagger-config", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> swaggerConfig() {
        return Mono.fromCallable(this::availableDocumentation).subscribeOn(Schedulers.boundedElastic());
    }

    Map<String, Object> availableDocumentation() {
        List<Map<String, String>> urls = new ArrayList<>();
        for (String service : PUBLIC_API_SERVICES) {
            if (!discovery.getInstances(service).isEmpty()) {
                urls.add(Map.of("name", service, "url", "/v3/api-docs/" + service));
            }
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("urls", urls);
        if (!urls.isEmpty()) {
            config.put("urls.primaryName", urls.get(0).get("name"));
        }
        return config;
    }
}
