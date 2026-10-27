package com.rental.listingservice.features.listing.infrastructure.persistence.repository;

import com.rental.listingservice.features.listing.domain.enums.ListingStatus;
import com.rental.listingservice.features.listing.infrastructure.persistence.entity.RoomListing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import java.util.List;

public interface RoomListingJpaRepository
        extends JpaRepository<RoomListing, Long>, JpaSpecificationExecutor<RoomListing> {

    boolean existsByRoomIdAndStatus(Long roomId, ListingStatus status);

    Optional<RoomListing> findByIdAndStatus(Long id, ListingStatus status);

    Optional<RoomListing> findFirstByRoomIdAndStatus(Long roomId, ListingStatus status);

    List<RoomListing> findAllByBuildingIdAndStatus(Long buildingId, ListingStatus status);
}
