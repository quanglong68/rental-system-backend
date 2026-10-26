package fptu.semester8.mss301.propertiesservice.features.building.infrastructure.persistence.adapter;

import fptu.semester8.mss301.propertiesservice.support.PostgresIntegrationSupport;
import fptu.semester8.mss301.propertiesservice.features.building.mapper.BuildingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JdbcBuildingSearchRepositoryIntegrationTest extends PostgresIntegrationSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired JdbcBuildingSearchRepository repository;
    @Autowired BuildingMapper mapper;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE building_images, building, ward, province CASCADE");
        jdbc.update("INSERT INTO province(province_id, code, name) VALUES (1, 'P', 'Province')");
        jdbc.update("INSERT INTO ward(ward_id, province_id, code, name) VALUES (2, 1, 'W', 'Ward')");
        jdbc.update("INSERT INTO building(building_id, province_id, ward_id, name, address, latitude, longitude) " +
                "VALUES (10, 1, 2, 'First', 'Address', 10, 106), (11, 1, 2, 'Second', 'Address', 10, 106)");
        jdbc.update("INSERT INTO building_images(building_image_id, building_id, image_url, display_order) VALUES " +
                "(103, 10, 'https://example.test/third.jpg', 2), " +
                "(102, 10, 'https://example.test/second.jpg', 0), " +
                "(101, 10, 'https://example.test/first.jpg', 0)");
    }

    @Test
    void buildingStatusDefaultsAndConstraintAreEnforced() {
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT building_status FROM building WHERE building_id=10", String.class));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE building SET building_status='INVALID' WHERE building_id=10"));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE building SET building_status=NULL WHERE building_id=10"));
        jdbc.update("UPDATE building SET building_status='MAINTENANCE' WHERE building_id=10");
        var item = repository.search(null, null, null, PageRequest.of(0, 10)).getContent().stream()
                .filter(b -> b.buildingId() == 10L).findFirst().orElseThrow();
        assertEquals("MAINTENANCE", mapper.toSearchResponse(item).buildingStatus());
    }

    @Test
    void searchReturnsFirstImageByDisplayOrderAndIdOrNullWhenMissing() {
        var page = repository.search(null, null, null, PageRequest.of(0, 10));

        assertEquals(2, page.getTotalElements());
        assertEquals("https://example.test/first.jpg", mapper.toSearchResponse(page.getContent().stream()
                .filter(item -> item.buildingId() == 10L).findFirst().orElseThrow()).buildingUrlImage());
        assertNull(page.getContent().stream()
                .filter(item -> item.buildingId() == 11L).findFirst().orElseThrow().buildingUrlImage());
    }

    @Test
    void nearbyAlsoReturnsFirstBuildingImage() {
        var page = repository.nearby(new BigDecimal("10"), new BigDecimal("106"), 1000, PageRequest.of(0, 10));

        assertEquals(2, page.getTotalElements());
        assertEquals("https://example.test/first.jpg", page.getContent().stream()
                .filter(item -> item.buildingId() == 10L).findFirst().orElseThrow().buildingUrlImage());
    }
}
