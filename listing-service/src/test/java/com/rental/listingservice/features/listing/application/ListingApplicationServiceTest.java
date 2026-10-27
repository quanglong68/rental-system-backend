package com.rental.listingservice.features.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rental.common.error.BusinessException;
import com.rental.listingservice.common.id.IdGenerator;
import com.rental.listingservice.features.listing.api.request.CreateListingRequest;
import com.rental.listingservice.features.listing.api.request.UpdateListingRequest;
import com.rental.listingservice.features.listing.domain.enums.ListingStatus;
import com.rental.listingservice.features.listing.domain.enums.RoomStatus;
import com.rental.listingservice.features.listing.infrastructure.persistence.entity.RoomListing;
import com.rental.listingservice.features.listing.infrastructure.persistence.repository.RoomListingJpaRepository;
import com.rental.listingservice.integration.property.ListingRoomSnapshot;
import com.rental.listingservice.integration.property.PropertyClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListingApplicationServiceTest {

    @Mock
    private RoomListingJpaRepository listings;
    @Mock
    private PropertyClient propertyClient;
    @Mock
    private ListingAuthorizationService authorization;
    @Mock
    private IdGenerator idGenerator;

    private ListingApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ListingApplicationService(listings, propertyClient, authorization, idGenerator);
    }

    @Test
    void createUsesPropertySnapshotAndApplicationGeneratedId() {
        when(propertyClient.getListingSnapshot(21L)).thenReturn(snapshot());
        when(idGenerator.nextId()).thenReturn(987654321L);
        when(listings.saveAndFlush(any(RoomListing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RoomListing created = service.create(new CreateListingRequest(21L, "Studio for rent", "Near transit"));

        assertThat(created.getId()).isEqualTo(987654321L);
        assertThat(created.getRoomId()).isEqualTo(21L);
        assertThat(created.getBuildingId()).isEqualTo(41L);
        assertThat(created.getRoomStatus()).isEqualTo(RoomStatus.VACANT);
        assertThat(created.getStatus()).isEqualTo(ListingStatus.ACTIVE);
        assertThat(created.getPrice()).isEqualByComparingTo("5000000");
        assertThat(created.getTitle()).isEqualTo("Studio for rent");
        verify(authorization).requireBuildingAccess(41L);
    }

    @Test
    void createRejectsSecondActiveListingForSameRoom() {
        when(propertyClient.getListingSnapshot(21L)).thenReturn(snapshot());
        when(listings.existsByRoomIdAndStatus(21L, ListingStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateListingRequest(21L, "Another title", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");

        verify(listings, never()).saveAndFlush(any());
    }

    @Test
    void updateRejectsClosedListing() {
        RoomListing closed = listing();
        closed.close(Instant.now());
        when(listings.findById(1L)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.update(1L, new UpdateListingRequest("Updated", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("closed listing");

        verify(authorization).requireBuildingAccess(41L);
    }

    @Test
    void closeChangesOnlyActiveListingToClosed() {
        RoomListing active = listing();
        when(listings.findById(1L)).thenReturn(Optional.of(active));
        when(listings.save(active)).thenReturn(active);

        RoomListing closed = service.close(1L);

        assertThat(closed.getStatus()).isEqualTo(ListingStatus.CLOSED);
        verify(authorization).requireBuildingAccess(41L);
    }

    private static ListingRoomSnapshot snapshot() {
        return new ListingRoomSnapshot(21L, 41L, "Building A", "1 Main Street", 11L, null, 31L,
                "Studio", "A-101", 2, "VACANT", new BigDecimal("5000000"),
                new BigDecimal("10.77690000"), new BigDecimal("106.70090000"));
    }

    private static RoomListing listing() {
        return new RoomListing(1L, 21L, 41L, "Building A", "1 Main Street", 11L, null, 31L,
                "Studio", "A-101", 2, new BigDecimal("5000000"), new BigDecimal("10.77690000"),
                new BigDecimal("106.70090000"), RoomStatus.VACANT, "Title", null, Instant.now());
    }
}
