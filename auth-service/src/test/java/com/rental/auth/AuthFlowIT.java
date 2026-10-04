package com.rental.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Nghiem thu Giai doan 0 (Task E1, Table 19): register -> login -> me -> refresh
 * rotation -> replay -> logout, tren Postgres + Kafka that (Testcontainers).
 * Gateway mo phong bang header X-User-* (Gateway da co unit test + live test rieng).
 * Chay bang maven-failsafe (mvn verify), can Docker.
 */
@Testcontainers
@SpringBootTest(classes = AuthServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthFlowIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @DynamicPropertySource
    static void infra(DynamicPropertyRegistry registry) {
        registry.add("AUTH_DB_URL", POSTGRES::getJdbcUrl);
        registry.add("AUTH_DB_USER", POSTGRES::getUsername);
        registry.add("AUTH_DB_PASSWORD", POSTGRES::getPassword);
        registry.add("KAFKA_BOOTSTRAP_SERVERS", KAFKA::getBootstrapServers);
    }

    @Autowired
    private TestRestTemplate rest;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void dangKy_dangNhap_me_refresh_logout_tronFlow() {
        // 1. Dang ky -> 201.
        ResponseEntity<JsonNode> registered = post("/api/v1/auth/register",
                Map.of("username", "flow01@gmail.com", "password", "matkhau123", "fullName", "Flow",
                        "email", "flow01@gmail.com"),
                null, HttpStatus.CREATED);
        long accountId = registered.getBody().get("accountId").asLong();
        assertThat(accountId).isPositive();

        // 2. Dang nhap -> 200 + cap token.
        ResponseEntity<JsonNode> loggedIn = post("/api/v1/auth/login",
                Map.of("username", "flow01@gmail.com", "password", "matkhau123"), null, HttpStatus.OK);
        String refresh1 = loggedIn.getBody().get("refreshToken").asText();
        assertThat(loggedIn.getBody().get("accessToken").asText()).isNotBlank();
        assertThat(loggedIn.getBody().get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(refresh1).isNotBlank();

        // 3. Me voi header Gateway -> 200.
        HttpHeaders gateway = gatewayHeaders(String.valueOf(accountId), "CUSTOMER", "CUSTOMER");
        ResponseEntity<JsonNode> me = rest.exchange("/api/v1/auth/me", HttpMethod.GET,
                new HttpEntity<>(gateway), JsonNode.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("username").asText()).isEqualTo("flow01@gmail.com");

        // 4. Refresh rotation -> cap moi; dung lai token cu -> 401 + thu hoi toan bo.
        ResponseEntity<JsonNode> rotated = post("/api/v1/auth/refresh", Map.of("refreshToken", refresh1), null,
                HttpStatus.OK);
        String refresh2 = rotated.getBody().get("refreshToken").asText();
        assertThat(refresh2).isNotEqualTo(refresh1);
        post("/api/v1/auth/refresh", Map.of("refreshToken", refresh1), null, HttpStatus.UNAUTHORIZED);
        post("/api/v1/auth/refresh", Map.of("refreshToken", refresh2), null, HttpStatus.UNAUTHORIZED);

        // 5. Dang nhap lai -> logout -> refresh that bai.
        ResponseEntity<JsonNode> relogin = post("/api/v1/auth/login",
                Map.of("username", "flow01@gmail.com", "password", "matkhau123"), null, HttpStatus.OK);
        String refresh3 = relogin.getBody().get("refreshToken").asText();
        post("/api/v1/auth/logout", Map.of("refreshToken", refresh3), gateway, HttpStatus.NO_CONTENT);
        post("/api/v1/auth/refresh", Map.of("refreshToken", refresh3), null, HttpStatus.UNAUTHORIZED);

        // 6. Event AccountRegistered da len topic auth.events dung envelope.
        JsonNode envelope = consumeAccountRegistered(refresh2);
        assertThat(envelope.get("eventType").asText()).isEqualTo("AccountRegistered");
        assertThat(envelope.get("version").asInt()).isEqualTo(1);
        assertThat(envelope.get("payload").get("accountId").asLong()).isEqualTo(accountId);
        assertThat(envelope.get("payload").get("username").asText()).isEqualTo("flow01@gmail.com");
    }

    private ResponseEntity<JsonNode> post(String path, Object body, HttpHeaders headers, HttpStatus expected) {
        HttpHeaders merged = new HttpHeaders();
        merged.setContentType(MediaType.APPLICATION_JSON);
        if (headers != null) {
            headers.forEach((name, values) -> merged.put(name, values));
        }
        ResponseEntity<JsonNode> response = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, merged), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(expected);
        return response;
    }

    private static HttpHeaders gatewayHeaders(String userId, String userType, String roles) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", userId);
        headers.set("X-User-Type", userType);
        headers.set("X-User-Roles", roles);
        return headers;
    }

    /** Doc topic auth.events toi da 30s cho toi khi thay event cua account vua dang ky. */
    private JsonNode consumeAccountRegistered(String knownRefreshToken) {
        var props = new java.util.HashMap<String, Object>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "auth-flow-it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("auth.events"));
            long deadline = System.currentTimeMillis() + 30_000;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(2));
                for (ConsumerRecord<String, String> record : records) {
                    JsonNode envelope = objectMapper.readTree(record.value());
                    if ("AccountRegistered".equals(envelope.path("eventType").asText())
                            && envelope.path("payload").path("username").asText().equals("flow01@gmail.com")) {
                        return envelope;
                    }
                }
            }
        } catch (Exception failed) {
            throw new AssertionError("Khong nhan duoc AccountRegistered tren topic auth.events", failed);
        }
        throw new AssertionError("Khong nhan duoc AccountRegistered tren topic auth.events");
    }
}