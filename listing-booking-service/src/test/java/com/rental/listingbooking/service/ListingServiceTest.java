package com.rental.listingbooking.service;

import com.rental.common.error.BusinessException;
import com.rental.common.error.NotFoundException;
import com.rental.listingbooking.dto.ListingResponse;
import com.rental.common.dto.PageResponse;
import com.rental.listingbooking.dto.UpdateListingRequest;
import com.rental.listingbooking.dto.UpdateListingStatusRequest;
import com.rental.listingbooking.entity.RoomListing;
import com.rental.listingbooking.repository.RoomListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ListingServiceTest {

    @Mock
    private RoomListingRepository listingRepository;

    @Mock
    private BuildingScope buildingScope;

    @InjectMocks
    private ListingService listingService;

    private RoomListing sampleListing;

    @BeforeEach
    void setUp() {
        sampleListing = new RoomListing();
        sampleListing.setId(1L);
        sampleListing.setRoomId(10L);
        sampleListing.setBuildingId(100L);
        sampleListing.setTitle("Phòng trọ cao cấp");
        sampleListing.setPrice(new BigDecimal("3500000"));
        sampleListing.setStatus("ACTIVE");
        sampleListing.setCreatedAt(Instant.now());
    }

    @Test
    void getById_success() {
        when(listingRepository.findById(1L)).thenReturn(Optional.of(sampleListing));

        ListingResponse response = listingService.getById(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getTitle()).isEqualTo("Phòng trọ cao cấp");
    }

    @Test
    void getById_notFound_throwsException() {
        when(listingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> listingService.getById(999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void search_withoutDistanceFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<RoomListing> page = new PageImpl<>(List.of(sampleListing), pageable, 1);
        when(listingRepository.searchActive(null, null, null, null, null, null, null, pageable))
                .thenReturn(page);

        PageResponse<ListingResponse> res = listingService.search(
                null, null, null, null, null, null, null, null, null, null, pageable);

        assertThat(res.getContent()).hasSize(1);
        assertThat(res.getTotalElements()).isEqualTo(1);
    }

    @Test
    void updateStatus_activeToClosed() {
        when(listingRepository.findById(1L)).thenReturn(Optional.of(sampleListing));
        when(listingRepository.save(any(RoomListing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ListingResponse response = listingService.updateStatus(1L, new UpdateListingStatusRequest("CLOSED"));

        assertThat(response.getStatus()).isEqualTo("CLOSED");
        verify(listingRepository).save(sampleListing);
    }

    @Test
    void updateStatus_activateOccupiedRoom_throwsRoomNotAvailable() {
        sampleListing.setStatus("CLOSED");
        sampleListing.setRoomStatus("OCCUPIED");
        when(listingRepository.findById(1L)).thenReturn(Optional.of(sampleListing));

        assertThatThrownBy(() -> listingService.updateStatus(1L, new UpdateListingStatusRequest("ACTIVE")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", "ROOM_NOT_AVAILABLE");
    }

    @Test
    void updateStatus_activateVacantRoom_succeeds() {
        sampleListing.setStatus("CLOSED");
        sampleListing.setRoomStatus("VACANT");
        when(listingRepository.findById(1L)).thenReturn(Optional.of(sampleListing));
        when(listingRepository.save(any(RoomListing.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ListingResponse response = listingService.updateStatus(1L, new UpdateListingStatusRequest("active"));

        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }
}
