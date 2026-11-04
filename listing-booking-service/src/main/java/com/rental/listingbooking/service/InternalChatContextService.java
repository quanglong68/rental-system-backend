package com.rental.listingbooking.service;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.listingbooking.dto.ChatAppointmentContext;
import com.rental.listingbooking.dto.ChatListingContext;
import com.rental.listingbooking.dto.ChatRentalRequestContext;
import com.rental.listingbooking.dto.ChatRoommateContext;
import com.rental.listingbooking.dto.ChatRoommatePostContext;
import com.rental.listingbooking.entity.Appointment;
import com.rental.listingbooking.entity.RentalRequest;
import com.rental.listingbooking.entity.RoomListing;
import com.rental.listingbooking.entity.RoommateApplication;
import com.rental.listingbooking.entity.RoommatePost;
import com.rental.listingbooking.repository.AppointmentRepository;
import com.rental.listingbooking.repository.RentalRequestRepository;
import com.rental.listingbooking.repository.RoomListingRepository;
import com.rental.listingbooking.repository.RoommateApplicationRepository;
import com.rental.listingbooking.repository.RoommatePostRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only adapter cho chat-service xac thuc ngu canh hoi thoai.
 * Chi tra du lieu toi thieu de xac dinh 2 ben va nhan hien thi.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalChatContextService {

    private final RoommateApplicationRepository applications;
    private final RoommatePostRepository roommatePosts;
    private final AppointmentRepository appointments;
    private final RentalRequestRepository rentalRequests;
    private final RoomListingRepository listings;

    public ChatRoommateContext roommateApplication(Long applicationId) {
        RoommateApplication application = applications.findById(applicationId)
                .orElseThrow(() -> notFound("Không tìm thấy đơn ứng tuyển ở ghép"));
        RoommatePost post = roommatePosts.findById(application.getRoommatePostId())
                .orElseThrow(() -> notFound("Không tìm thấy tin ở ghép"));
        Optional<RoomListing> listing = listings.findByRoomId(post.getRoomId());
        return new ChatRoommateContext(application.getId(), post.getId(), post.getCustomerId(),
                application.getCustomerId(), post.getTitle(), post.getRoomId(), post.getBuildingId(),
                listing.map(RoomListing::getRoomNumber).orElse(null),
                listing.map(RoomListing::getBuildingName).orElse(null));
    }

    public ChatRoommatePostContext roommatePost(Long postId) {
        RoommatePost post = roommatePosts.findById(postId)
                .orElseThrow(() -> notFound("Không tìm thấy tin ở ghép"));
        Optional<RoomListing> listing = listings.findByRoomId(post.getRoomId());
        return new ChatRoommatePostContext(post.getId(), post.getCustomerId(), post.getTitle(),
                post.getRoomId(), post.getBuildingId(),
                listing.map(RoomListing::getRoomNumber).orElse(null),
                listing.map(RoomListing::getBuildingName).orElse(null));
    }

    public ChatListingContext listing(Long listingId) {
        RoomListing listing = listings.findById(listingId)
                .orElseThrow(() -> notFound("Không tìm thấy tin đăng"));
        return new ChatListingContext(listing.getId(), listing.getBuildingId(), listing.getRoomId(),
                listing.getRoomNumber(), listing.getBuildingName(), listing.getTitle());
    }

    public ChatAppointmentContext appointment(Long appointmentId) {
        Appointment appointment = appointments.findById(appointmentId)
                .orElseThrow(() -> notFound("Không tìm thấy lịch xem phòng"));
        Optional<RoomListing> listing = appointment.getRoomId() == null
                ? Optional.empty()
                : listings.findByRoomId(appointment.getRoomId());
        Long buildingId = appointment.getBuildingId() != null
                ? appointment.getBuildingId()
                : listing.map(RoomListing::getBuildingId).orElse(null);
        return new ChatAppointmentContext(appointment.getId(), appointment.getCustomerId(),
                appointment.getStaffId(), buildingId, appointment.getRoomId(), appointment.getAppointmentDate(),
                appointment.getStatus(), listing.map(RoomListing::getRoomNumber).orElse(null),
                listing.map(RoomListing::getBuildingName).orElse(null));
    }

    public ChatRentalRequestContext rentalRequest(Long rentalRequestId) {
        RentalRequest request = rentalRequests.findById(rentalRequestId)
                .orElseThrow(() -> notFound("Không tìm thấy đơn thuê"));
        Optional<RoomListing> listing = listings.findByRoomId(request.getRoomId());
        return new ChatRentalRequestContext(request.getId(), request.getCustomerId(), request.getListingId(),
                request.getBuildingId(), request.getRoomId(), request.getStatus(), request.getVerifiedBy(),
                listing.map(RoomListing::getRoomNumber).orElse(null),
                listing.map(RoomListing::getBuildingName).orElse(null));
    }

    private static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }
}
