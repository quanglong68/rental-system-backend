package com.rental.listingbooking.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.listingbooking.config.ListingAvailabilitySettings;
import com.rental.listingbooking.entity.RentalRequest;
import com.rental.listingbooking.entity.RoomListing;
import com.rental.listingbooking.outbox.ListingEventPublisher;
import com.rental.listingbooking.outbox.RentalRequestRejectedPayload;
import com.rental.listingbooking.repository.ProcessedEventRepository;
import com.rental.listingbooking.repository.RentalRequestRepository;
import com.rental.listingbooking.repository.RoomListingRepository;
import com.rental.listingbooking.repository.RoommateApplicationRepository;
import com.rental.listingbooking.repository.RoommatePostRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class ContractEventConsumerTest {

    @Mock
    private RoomListingRepository listingRepository;
    @Mock
    private RentalRequestRepository rentalRequestRepository;
    @Mock
    private RoommatePostRepository roommatePostRepository;
    @Mock
    private RoommateApplicationRepository applicationRepository;
    @Mock
    private ProcessedEventRepository processedEvents;
    @Mock
    private ListingEventPublisher publisher;
    @Mock
    private PlatformTransactionManager transactionManager;

    private ContractEventConsumer consumer;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        consumer = new ContractEventConsumer(
                listingRepository,
                rentalRequestRepository,
                roommatePostRepository,
                applicationRepository,
                processedEvents,
                publisher,
                new ListingAvailabilitySettings(10),
                transactionManager,
                new ObjectMapper());
    }

    @Test
    void moveOutApproved_reopensListingWithCleaningBuffer() {
        RoomListing listing = listing();
        when(listingRepository.findByRoomId(5L)).thenReturn(Optional.of(listing));
        LocalDate moveOutDate = LocalDate.now().plusDays(20);

        consumer.consume(event("MoveOutApproved",
                "{\"roomId\":5,\"contractId\":9,\"expectedMoveOutDate\":\"" + moveOutDate + "\"}"));

        assertThat(listing.getStatus()).isEqualTo("ACTIVE");
        assertThat(listing.getRoomStatus()).isEqualTo("UPCOMING_VACANT");
        assertThat(listing.getAvailableFrom()).isEqualTo(moveOutDate.plusDays(10));
        verify(listingRepository).save(listing);
    }

    @Test
    void contractEnded_withNextContract_keepsListingClosed() {
        consumer.consume(event("ContractEnded",
                "{\"roomId\":5,\"contractId\":9,\"endDate\":\"2026-10-01\",\"nextContractExists\":true}"));

        verify(listingRepository, never()).findByRoomId(any());
        verify(listingRepository, never()).save(any());
    }

    @Test
    void contractEnded_withoutNextContract_reopensFromTodayWhenBufferPassed() {
        RoomListing listing = listing();
        when(listingRepository.findByRoomId(5L)).thenReturn(Optional.of(listing));
        LocalDate endDate = LocalDate.now().minusDays(20);

        consumer.consume(event("ContractEnded",
                "{\"roomId\":5,\"contractId\":9,\"endDate\":\"" + endDate + "\",\"nextContractExists\":false}"));

        assertThat(listing.getStatus()).isEqualTo("ACTIVE");
        assertThat(listing.getRoomStatus()).isEqualTo("VACANT");
        assertThat(listing.getAvailableFrom()).isEqualTo(LocalDate.now());
    }

    @Test
    void contractSigned_rejectsCompetingRequestsButKeepsTheSignedOne() {
        RoomListing listing = listing();
        listing.setStatus("ACTIVE");
        listing.setRoomStatus("VACANT");
        when(listingRepository.findByRoomId(5L)).thenReturn(Optional.of(listing));

        RentalRequest signed = request(77L, "ACCEPTED");
        RentalRequest competitor = request(78L, "PENDING");
        when(rentalRequestRepository.findByRoomIdAndStatusIn(5L, List.of("PENDING", "VERIFIED", "ACCEPTED")))
                .thenReturn(List.of(signed, competitor));

        consumer.consume(event("ContractSigned",
                "{\"contractId\":9,\"roomId\":5,\"rentalRequestId\":77,\"startDate\":\"2026-11-01\"}"));

        assertThat(signed.getStatus()).isEqualTo("ACCEPTED");
        assertThat(competitor.getStatus()).isEqualTo("REJECTED");
        assertThat(competitor.getRejectReason())
                .isEqualTo("Phòng đã có người thuê trước. Hợp đồng có hiệu lực từ 2026-11-01.");
        assertThat(listing.getStatus()).isEqualTo("CLOSED");
        assertThat(listing.getRoomStatus()).isEqualTo("OCCUPIED");

        ArgumentCaptor<RentalRequestRejectedPayload> published =
                ArgumentCaptor.forClass(RentalRequestRejectedPayload.class);
        verify(publisher).publishRentalRequestRejected(published.capture());
        assertThat(published.getValue().rentalRequestId()).isEqualTo(78L);
    }

    private static RentalRequest request(Long id, String status) {
        RentalRequest request = new RentalRequest();
        request.setId(id);
        request.setCustomerId(id + 1000);
        request.setRoomId(5L);
        request.setStatus(status);
        return request;
    }

    private static RoomListing listing() {
        RoomListing listing = new RoomListing();
        listing.setId(1L);
        listing.setRoomId(5L);
        listing.setBuildingId(3L);
        listing.setTitle("Phòng 101");
        listing.setStatus("CLOSED");
        listing.setRoomStatus("OCCUPIED");
        return listing;
    }

    private static String event(String eventType, String payload) {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"" + eventType
                + "\",\"payload\":" + payload + "}";
    }
}
