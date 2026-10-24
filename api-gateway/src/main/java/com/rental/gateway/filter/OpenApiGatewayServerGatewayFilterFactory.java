package com.rental.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/** Adapts a service's OpenAPI document for Swagger UI served by the gateway. */
@Component
public class OpenApiGatewayServerGatewayFilterFactory
        extends AbstractGatewayFilterFactory<OpenApiGatewayServerGatewayFilterFactory.Config> {

    private static final String BEARER_SCHEME = "bearerAuth";
    private static final Set<String> HTTP_OPERATIONS = Set.of(
            "get", "post", "put", "patch", "delete", "head", "options", "trace");

    private final ModifyResponseBodyGatewayFilterFactory modifyResponseBody;
    private final ObjectMapper json;
    private final GatewayWhitelist whitelist;

    public OpenApiGatewayServerGatewayFilterFactory(ModifyResponseBodyGatewayFilterFactory modifyResponseBody,
            ObjectMapper json, GatewayWhitelist whitelist) {
        super(Config.class);
        this.modifyResponseBody = modifyResponseBody;
        this.json = json;
        this.whitelist = whitelist;
    }

    @Override
    public GatewayFilter apply(Config config) {
        var bodyConfig = new ModifyResponseBodyGatewayFilterFactory.Config()
                .setRewriteFunction(String.class, String.class,
                        (exchange, body) -> Mono.justOrEmpty(rewrite(body)));
        return modifyResponseBody.apply(bodyConfig);
    }

    String rewrite(String body) {
        if (body == null) {
            return null;
        }
        try {
            JsonNode document = json.readTree(body);
            if (!(document instanceof ObjectNode openApi) || !openApi.has("openapi")) {
                return body;
            }
            openApi.putArray("servers").addObject().put("url", "/");
            openApi.with("components").with("securitySchemes").putObject(BEARER_SCHEME)
                    .put("type", "http").put("scheme", "bearer").put("bearerFormat", "JWT")
                    .put("description", "Enter the JWT access token; Swagger UI adds the Bearer prefix.");
            if (openApi.path("paths") instanceof ObjectNode paths) {
                var internalPaths = new ArrayList<String>();
                paths.fieldNames().forEachRemaining(path -> {
                    if (path.equals("/internal") || path.startsWith("/internal/")) {
                        internalPaths.add(path);
                    }
                });
                paths.remove(internalPaths);
                paths.properties().forEach(path -> addBearerToProtectedOperations(path.getKey(), path.getValue()));
            }
            return json.writeValueAsString(openApi);
        } catch (JsonProcessingException invalidJson) {
            return body;
        }
    }

    private void addBearerToProtectedOperations(String path, JsonNode pathItem) {
        if (!path.startsWith("/api/") || !(pathItem instanceof ObjectNode operations)) {
            return;
        }
        operations.properties().forEach(entry -> {
            String method = entry.getKey().toLowerCase(Locale.ROOT);
            if (!HTTP_OPERATIONS.contains(method) || !(entry.getValue() instanceof ObjectNode operation)
                    || whitelist.isPublic(HttpMethod.valueOf(method.toUpperCase(Locale.ROOT)), path)) {
                return;
            }
            JsonNode existing = operation.get("security");
            ArrayNode requirements = existing instanceof ArrayNode array
                    ? array : operation.putArray("security");
            if (requirements.isEmpty()) {
                requirements.addObject().putArray(BEARER_SCHEME);
            } else {
                requirements.forEach(requirement -> {
                    if (requirement instanceof ObjectNode option && !option.has(BEARER_SCHEME)) {
                        option.putArray(BEARER_SCHEME);
                    }
                });
            }
        });
    }

    public static class Config {
    }
}
