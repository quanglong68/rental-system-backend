package fptu.semester8.mss301.propertiesservice.support;

import fptu.semester8.mss301.propertiesservice.features.events.OutboxRelay;
import fptu.semester8.mss301.propertiesservice.features.roomoccupant.application.OccupancyDateRefreshJob;
import fptu.semester8.mss301.propertiesservice.features.roomprice.application.RoomPriceAnnouncementJob;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import fptu.semester8.mss301.propertiesservice.config.PropertyGatewayTrustFilter;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import io.minio.MinioClient;
import java.time.Clock;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.listener.auto-startup=false", "spring.kafka.listener.missing-topics-fatal=false",
        "spring.kafka.admin.auto-create=false",
        "eureka.client.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresIntegrationSupport.GatewayRequestFixture.class)
public abstract class PostgresIntegrationSupport {
    @TestConfiguration(proxyBeanMethods = false)
    static class GatewayRequestFixture {
        @Bean
        MockMvcBuilderCustomizer trustedGatewayRequest() {
            return builder -> builder.defaultRequest(get("/")
                    .header(PropertyGatewayTrustFilter.HEADER,
                            "test-only-property-gateway-key-32-bytes-minimum"));
        }
    }
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:17-3.5").asCompatibleSubstituteFor("postgres"));
    static { POSTGRES.start(); }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
                + "currentSchema=properties_service,public");
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @MockitoBean protected Clock clock;
    @MockitoBean protected MinioClient minioClient;
    @MockitoBean protected OutboxRelay outboxRelay;
    @MockitoBean protected OccupancyDateRefreshJob occupancyDateRefreshJob;
    @MockitoBean protected RoomPriceAnnouncementJob roomPriceAnnouncementJob;
}
