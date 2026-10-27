package com.rental.listingservice.features.listing.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rental.listingservice.features.listing.domain.enums.ListingStatus;
import com.rental.listingservice.features.listing.domain.enums.RoomStatus;
import com.rental.listingservice.features.listing.infrastructure.persistence.entity.RoomListing;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RoomListingJpaRepositoryTest {

    @Autowired
    private RoomListingJpaRepository listings;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void entityMappingMatchesRoomListingMigrationAndStoresEnumValues() {
        listings.saveAndFlush(listing(7001L, ListingStatus.ACTIVE));

        var row = jdbc.queryForMap("SELECT status, room_status, price, latitude, longitude FROM room_listing WHERE room_listing_id = ?", 7001L);
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("room_status")).isEqualTo("VACANT");
        assertThat(row.get("price")).isEqualTo(new BigDecimal("5000000"));
        assertThat(listings.existsByRoomIdAndStatus(101L, ListingStatus.ACTIVE)).isTrue();
    }

    @Test
    void postgresPartialUniqueIndexAllowsOnlyOneActiveListingPerRoom() {
        listings.saveAndFlush(listing(7002L, ListingStatus.ACTIVE));

        assertThatThrownBy(() -> listings.saveAndFlush(listing(7003L, ListingStatus.ACTIVE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void closedHistoricalListingDoesNotBlockANewActiveListing() {
        listings.saveAndFlush(listing(7004L, ListingStatus.CLOSED));
        listings.saveAndFlush(listing(7005L, ListingStatus.ACTIVE));

        assertThat(listings.existsByRoomIdAndStatus(101L, ListingStatus.ACTIVE)).isTrue();
    }

    private static RoomListing listing(long id, ListingStatus status) {
        RoomListing listing = new RoomListing(id, 101L, 201L, "Building", "Address", 301L, null,
                401L, "Studio", "A-101", 2, new BigDecimal("5000000"), new BigDecimal("10.77690000"),
                new BigDecimal("106.70090000"), RoomStatus.VACANT, "Listing title", null, Instant.now());
        if (status == ListingStatus.CLOSED) {
            listing.close(Instant.now());
        }
        return listing;
    }
}
