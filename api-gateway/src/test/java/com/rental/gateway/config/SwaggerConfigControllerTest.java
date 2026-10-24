package com.rental.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

class SwaggerConfigControllerTest {

    @Test
    void listsOnlyRunningPublicApiServices() {
        DiscoveryClient discovery = mock(DiscoveryClient.class);
        when(discovery.getInstances("auth-service")).thenReturn(List.of(mock(ServiceInstance.class)));
        when(discovery.getInstances("property-service")).thenReturn(List.of(mock(ServiceInstance.class)));

        var config = new SwaggerConfigController(discovery).availableDocumentation();

        assertThat(config.get("urls")).isEqualTo(List.of(
                java.util.Map.of("name", "auth-service", "url", "/v3/api-docs/auth-service"),
                java.util.Map.of("name", "property-service", "url", "/v3/api-docs/property-service")));
        assertThat(config.get("urls.primaryName")).isEqualTo("auth-service");
    }
}
