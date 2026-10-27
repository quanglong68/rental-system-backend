package com.rental.listingservice.features.listing.application;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListingApplicationService {

    private final RoomListingJpaRepository listings;
    private final PropertyClient propertyClient;
    private final ListingAuthorizationService authorization;
    private final IdGenerator idGenerator;

    public ListingApplicationService(RoomListingJpaRepository listings, PropertyClient propertyClient,
            ListingAuthorizationService authorization, IdGenerator idGenerator) {
        this.listings = listings;
        this.propertyClient = propertyClient;
        this.authorization = authorization;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public RoomListing create(CreateListingRequest request) {
        ListingRoomSnapshot snapshot = propertyClient.getListingSnapshot(request.roomId());
        validateSnapshot(snapshot);
        authorization.requireBuildingAccess(snapshot.buildingId());

        if (listings.existsByRoomIdAndStatus(snapshot.roomId(), ListingStatus.ACTIVE)) {
            throw conflict("ACTIVE_LISTING_EXISTS", "An active listing already exists for this room");
        }

        RoomListing listing = new RoomListing(idGenerator.nextId(), snapshot.roomId(), snapshot.buildingId(),
                snapshot.buildingName(), snapshot.address(), snapshot.wardId(), snapshot.districtId(),
                snapshot.roomTypeId(), snapshot.roomTypeName(), snapshot.roomNumber(), snapshot.maxCapacity(),
                snapshot.price(), snapshot.latitude(), snapshot.longitude(), RoomStatus.fromValue(snapshot.roomStatus()),
                request.title().trim(), request.description(), Instant.now());
        try {
            return listings.saveAndFlush(listing);
        } catch (DataIntegrityViolationException exception) {
            throw conflict("ACTIVE_LISTING_EXISTS", "An active listing already exists for this room");
        }
    }

    @Transactional(readOnly = true)
    public RoomListing getActive(long id) {
        return listings.findByIdAndStatus(id, ListingStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Listing not found"));
    }

    @Transactional(readOnly = true)
    public Page<RoomListing> searchActive(Long buildingId, Long wardId, Long districtId, Long roomTypeId,
            BigDecimal minPrice, BigDecimal maxPrice, String keyword, Pageable pageable) {
        Specification<RoomListing> specification = (root, query, builder) -> builder.equal(
                root.get("status"), ListingStatus.ACTIVE);
        if (buildingId != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("buildingId"), buildingId));
        }
        if (wardId != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("wardId"), wardId));
        }
        if (districtId != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("districtId"), districtId));
        }
        if (roomTypeId != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("roomTypeId"), roomTypeId));
        }
        if (minPrice != null) {
            specification = specification.and((root, query, builder) -> builder.greaterThanOrEqualTo(root.get("price"), minPrice));
        }
        if (maxPrice != null) {
            specification = specification.and((root, query, builder) -> builder.lessThanOrEqualTo(root.get("price"), maxPrice));
        }
        if (keyword != null && !keyword.isBlank()) {
            String pattern = "%" + keyword.trim().toLowerCase() + "%";
            specification = specification.and((root, query, builder) -> builder.like(
                    builder.lower(root.get("title")), pattern));
        }
        return listings.findAll(specification, pageable);
    }

    @Transactional
    public RoomListing update(long id, UpdateListingRequest request) {
        RoomListing listing = listings.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Listing not found"));
        authorization.requireBuildingAccess(listing.getBuildingId());
        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw conflict("LISTING_NOT_ACTIVE", "A closed listing cannot be updated");
        }
        listing.updateContent(request.title().trim(), request.description(), Instant.now());
        return listings.save(listing);
    }

    @Transactional
    public RoomListing close(long id) {
        RoomListing listing = listings.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Listing not found"));
        authorization.requireBuildingAccess(listing.getBuildingId());
        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw conflict("LISTING_NOT_ACTIVE", "Only an active listing can be closed");
        }
        listing.close(Instant.now());
        return listings.save(listing);
    }

    private static void validateSnapshot(ListingRoomSnapshot snapshot) {
        if (snapshot == null || snapshot.roomId() == null || snapshot.buildingId() == null
                || snapshot.buildingName() == null || snapshot.address() == null || snapshot.roomTypeId() == null
                || snapshot.roomTypeName() == null || snapshot.roomNumber() == null || snapshot.maxCapacity() == null
                || snapshot.roomStatus() == null || snapshot.price() == null) {
            throw conflict("PROPERTY_SNAPSHOT_INCOMPLETE", "Property service returned incomplete listing data");
        }
        if (snapshot.price().signum() < 0 || snapshot.maxCapacity() < 1
                || snapshot.latitude() != null && (snapshot.latitude().compareTo(BigDecimal.valueOf(-90)) < 0
                        || snapshot.latitude().compareTo(BigDecimal.valueOf(90)) > 0)
                || snapshot.longitude() != null && (snapshot.longitude().compareTo(BigDecimal.valueOf(-180)) < 0
                        || snapshot.longitude().compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw conflict("PROPERTY_SNAPSHOT_INVALID", "Property service returned invalid listing data");
        }
        RoomStatus.fromValue(snapshot.roomStatus());
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }
}
