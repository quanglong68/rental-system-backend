package com.rental.listingbooking.service;

import com.rental.common.error.BusinessException;
import com.rental.listingbooking.dto.RentalRequestCreateDto;
import com.rental.listingbooking.dto.RentalRequestResponse;
import com.rental.listingbooking.entity.RentalRequest;
import com.rental.listingbooking.entity.RoomListing;
import com.rental.listingbooking.outbox.ListingEventPublisher;
import com.rental.listingbooking.repository.RentalRequestRepository;
import com.rental.listingbooking.repository.RoomListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RentalRequestServiceTest {

    @Mock
    private RentalRequestRepository requestRepository;

    @Mock
    private RoomListingRepository listingRepository;

    @Mock
    private ListingEventPublisher publisher;

    @Mock
    private BuildingScope buildingScope;

    @Mock
    private IdentityFileStorage identityFileStorage;

    private RentalRequestService requestService;

    private RoomListing sampleListing;

    @BeforeEach
    void setUp() {
        requestService = new RentalRequestService(requestRepository, listingRepository, publisher,
                buildingScope, identityFileStorage, 30);
        sampleListing = new RoomListing();
        sampleListing.setId(1L);
        sampleListing.setRoomId(10L);
        sampleListing.setBuildingId(100L);
        sampleListing.setPrice(new BigDecimal("4000000"));
        sampleListing.setStatus("ACTIVE");
        sampleListing.setDepositAmount(new BigDecimal("4000000"));
        sampleListing.setElectricityPrice(new BigDecimal("3500"));
        sampleListing.setWaterPrice(new BigDecimal("100000"));
        sampleListing.setWaterUnit("PERSON");
        sampleListing.setPaymentMethod("BANK_TRANSFER");
        sampleListing.setPaymentDueDay(5);
        sampleListing.setMinTermMonths(12);
    }

    private static RentalRequestCreateDto dossier(Long listingId, LocalDate moveInDate, String note) {
        return new RentalRequestCreateDto(
                listingId, "Nguyễn Văn B", LocalDate.of(1995, 3, 10), "MALE", "0901234567",
                "b@example.com", "123 Lê Lợi, Q1", "Nhân viên", "079095001234",
                LocalDate.of(2021, 5, 20), "Cục Cảnh sát QLHC về TTXH",
                "identity/200/front.jpg", "identity/200/back.jpg",
                moveInDate, note, true);
    }

    @Test
    void createRequest_success() {
        when(listingRepository.findById(1L)).thenReturn(Optional.of(sampleListing));
        when(requestRepository.findByCustomerIdAndListingIdAndStatusIn(eq(200L), eq(1L), any()))
                .thenReturn(List.of());
        when(requestRepository.save(any(RentalRequest.class))).thenAnswer(invocation -> {
            RentalRequest r = invocation.getArgument(0);
            r.setId(99L);
            return r;
        });

        RentalRequestResponse res = requestService.createRequest(
                dossier(1L, LocalDate.now().plusDays(5), "Cần dọn vào sớm"), 200L);

        assertThat(res.id()).isEqualTo(99L);
        assertThat(res.status()).isEqualTo("PENDING");
        assertThat(res.rentAmount()).isEqualByComparingTo("4000000");
        assertThat(res.depositAmount()).isEqualByComparingTo("4000000");
        assertThat(res.termsAccepted()).isTrue();
        verify(publisher).publishRentalRequestCreated(any());
    }

    @Test
    void createRequest_termsNotAccepted_throwsException() {
        when(listingRepository.findById(1L)).thenReturn(Optional.of(sampleListing));
        RentalRequestCreateDto base = dossier(1L, LocalDate.now().plusDays(5), null);
        RentalRequestCreateDto dto = new RentalRequestCreateDto(base.listingId(), base.fullName(),
                base.dateOfBirth(), base.gender(), base.phone(), base.email(), base.permanentAddress(),
                base.occupation(), base.cccdNumber(), base.cccdIssueDate(), base.cccdIssuePlace(),
                base.cccdFrontKey(), base.cccdBackKey(), base.moveInDate(), base.note(), false);

        assertThatThrownBy(() -> requestService.createRequest(dto, 200L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("điều khoản");
    }

    @Test
    void createRequest_listingWithoutTerms_throwsException() {
        sampleListing.setDepositAmount(null);
        when(listingRepository.findById(1L)).thenReturn(Optional.of(sampleListing));

        assertThatThrownBy(() -> requestService.createRequest(
                dossier(1L, LocalDate.now().plusDays(5), null), 200L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("điều khoản hợp đồng");
    }

    @Test
    void createRequest_moveInDateTooFar_throwsException() {
        when(listingRepository.findById(1L)).thenReturn(Optional.of(sampleListing));
        when(requestRepository.findByCustomerIdAndListingIdAndStatusIn(eq(200L), eq(1L), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> requestService.createRequest(
                dossier(1L, LocalDate.now().plusDays(60), null), 200L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Ngày dự kiến vào ở");
    }

    @Test
    void createRequest_inactiveListing_throwsException() {
        sampleListing.setStatus("CLOSED");
        when(listingRepository.findById(1L)).thenReturn(Optional.of(sampleListing));

        assertThatThrownBy(() -> requestService.createRequest(
                dossier(1L, LocalDate.now().plusDays(1), null), 200L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void verifyRequest_success() {
        RentalRequest targetRequest = new RentalRequest();
        targetRequest.setId(10L);
        targetRequest.setBuildingId(100L);
        targetRequest.setStatus("PENDING");

        when(requestRepository.findById(10L)).thenReturn(Optional.of(targetRequest));
        when(requestRepository.save(any(RentalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RentalRequestResponse res = requestService.verifyRequest(10L, 77L);

        assertThat(res.status()).isEqualTo("VERIFIED");
        assertThat(res.verifiedBy()).isEqualTo(77L);
        assertThat(res.verifiedAt()).isNotNull();
    }

    @Test
    void verifyRequest_alreadyVerified_throwsException() {
        RentalRequest targetRequest = new RentalRequest();
        targetRequest.setId(10L);
        targetRequest.setBuildingId(100L);
        targetRequest.setStatus("VERIFIED");

        when(requestRepository.findById(10L)).thenReturn(Optional.of(targetRequest));

        assertThatThrownBy(() -> requestService.verifyRequest(10L, 77L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void acceptRequest_success_doesNotRejectCompetitors() {
        RentalRequest targetRequest = new RentalRequest();
        targetRequest.setId(10L);
        targetRequest.setRoomId(10L);
        targetRequest.setListingId(1L);
        targetRequest.setBuildingId(100L);
        targetRequest.setCustomerId(200L);
        targetRequest.setStatus("VERIFIED");
        targetRequest.setMoveInDate(LocalDate.now());

        RentalRequest competingRequest = new RentalRequest();
        competingRequest.setId(11L);
        competingRequest.setRoomId(10L);
        competingRequest.setCustomerId(201L);
        competingRequest.setStatus("PENDING");

        when(requestRepository.findById(10L)).thenReturn(Optional.of(targetRequest));
        when(listingRepository.findById(1L)).thenReturn(Optional.of(sampleListing));
        when(requestRepository.save(any(RentalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RentalRequestResponse res = requestService.acceptRequest(10L);

        assertThat(res.status()).isEqualTo("ACCEPTED");
        assertThat(competingRequest.getStatus()).isEqualTo("PENDING");
        verify(publisher).publishRentalRequestAccepted(any());
        verify(publisher, never()).publishRentalRequestRejected(any());
    }

    @Test
    void acceptRequest_pendingRequest_throwsException() {
        RentalRequest targetRequest = new RentalRequest();
        targetRequest.setId(10L);
        targetRequest.setBuildingId(100L);
        targetRequest.setStatus("PENDING");

        when(requestRepository.findById(10L)).thenReturn(Optional.of(targetRequest));

        assertThatThrownBy(() -> requestService.acceptRequest(10L))
                .isInstanceOf(BusinessException.class);
    }
}
