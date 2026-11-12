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

    @Test
    void listsAllEightServicesIncludingChatWhenRegistered() {
        DiscoveryClient discovery = mock(DiscoveryClient.class);
        List<String> allServices = List.of(
                "auth-service", "user-notification-service", "property-service",
                "listing-booking-service", "chat-service", "contract-tenant-service",
                "billing-utility-service", "asset-maintenance-service");
        for (String service : allServices) {
            when(discovery.getInstances(service)).thenReturn(List.of(mock(ServiceInstance.class)));
        }

        var config = new SwaggerConfigController(discovery).availableDocumentation();

        @SuppressWarnings("unchecked")
        var urls = (List<java.util.Map<String, String>>) config.get("urls");
        assertThat(urls).extracting(item -> item.get("name")).containsExactlyElementsOf(allServices);
    }

    @Test
    void rootRedirectsToSwaggerUi() {
        var controller = new SwaggerConfigController(mock(DiscoveryClient.class));
        var response = controller.rootRedirect().block();
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getLocation()).hasToString("/swagger-ui.html");
    }
}
