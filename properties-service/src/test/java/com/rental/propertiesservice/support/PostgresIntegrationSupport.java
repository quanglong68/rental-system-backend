package com.rental.propertiesservice.support;

import com.rental.propertiesservice.client.StaffClient;
import com.rental.propertiesservice.features.events.OutboxRelay;
import com.rental.propertiesservice.features.roomoccupant.application.OccupancyDateRefreshJob;
import com.rental.propertiesservice.features.roomprice.application.RoomPriceAnnouncementJob;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.*;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import java.time.Clock;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.listener.auto-startup=false", "spring.kafka.admin.auto-create=false",
        "eureka.client.enabled=false"
})
@AutoConfigureMockMvc
public abstract class PostgresIntegrationSupport {
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
    @MockBean protected Clock clock;
    @MockBean protected S3Client s3Client;
    @MockBean protected OutboxRelay outboxRelay;
    @MockBean protected OccupancyDateRefreshJob occupancyDateRefreshJob;
    @MockBean protected RoomPriceAnnouncementJob roomPriceAnnouncementJob;
    @MockBean protected StaffClient staffClient;
}
