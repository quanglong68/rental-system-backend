package com.rental.listingbooking.service;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.error.NotFoundException;
import com.rental.listingbooking.dto.AppointmentCreateDto;
import com.rental.listingbooking.dto.AppointmentResponse;
import com.rental.common.dto.PageResponse;
import com.rental.listingbooking.dto.UpdateAppointmentStatusDto;
import com.rental.listingbooking.entity.Appointment;
import com.rental.listingbooking.entity.RentalRequest;
import com.rental.listingbooking.entity.RoomListing;
import com.rental.listingbooking.outbox.AppointmentCreatedPayload;
import com.rental.listingbooking.outbox.AppointmentStatusChangedPayload;
import com.rental.listingbooking.outbox.ListingEventPublisher;
import com.rental.listingbooking.repository.AppointmentRepository;
import com.rental.listingbooking.repository.RentalRequestRepository;
import com.rental.listingbooking.repository.RoomListingRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final Set<String> STAFF_TARGET_STATUSES = Set.of("CONFIRMED", "VIEWED", "CANCELLED");
    private static final Instant MIN_TIME = Instant.EPOCH;
    private static final Instant MAX_TIME = Instant.parse("9999-12-31T23:59:59Z");
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "SCHEDULED", Set.of("CONFIRMED", "VIEWED", "CANCELLED"),
            "CONFIRMED", Set.of("VIEWED", "CANCELLED"),
            "VIEWED", Set.of(),
            "CANCELLED", Set.of());

    private final AppointmentRepository appointmentRepository;
    private final RentalRequestRepository rentalRequestRepository;
    private final RoomListingRepository listingRepository;
    private final ListingEventPublisher publisher;
    private final BuildingScope buildingScope;

    @Transactional
    public AppointmentResponse createAppointment(AppointmentCreateDto dto) {
        RentalRequest rentalRequest = rentalRequestRepository.findById(dto.rentalRequestId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy yêu cầu thuê ID " + dto.rentalRequestId()));

        if (!"PENDING".equals(rentalRequest.getStatus())
                && !"VERIFIED".equals(rentalRequest.getStatus())
                && !"ACCEPTED".equals(rentalRequest.getStatus())) {
            throw new BusinessException("INVALID_STATUS",
                    "Không thể tạo lịch hẹn cho yêu cầu thuê đã bị hủy hoặc từ chối",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }

        buildingScope.assertAssigned(rentalRequest.getBuildingId());
        buildingScope.assertStaffAssigned(dto.staffId(), rentalRequest.getBuildingId());

        if (dto.appointmentDate().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thời gian hẹn phải ở tương lai");
        }

        Appointment appointment = new Appointment();
        appointment.setRentalRequestId(rentalRequest.getId());
        appointment.setCustomerId(rentalRequest.getCustomerId());
        appointment.setStaffId(dto.staffId());
        appointment.setBuildingId(rentalRequest.getBuildingId());
        appointment.setAppointmentDate(dto.appointmentDate());
        appointment.setStatus("SCHEDULED");
        appointment.setCreatedAt(Instant.now());

        Appointment saved = appointmentRepository.save(appointment);

        publisher.publishAppointmentScheduled(new AppointmentCreatedPayload(
                saved.getId(),
                saved.getRentalRequestId(),
                saved.getCustomerId(),
                saved.getStaffId(),
                saved.getBuildingId(),
                saved.getAppointmentDate()
        ));

        return toResponse(saved);
    }

    /** Customer self-book viewing appointment before sending a rental request. */
    @Transactional
    public AppointmentResponse createForCustomer(Long customerId, AppointmentCreateDto dto) {
        if (dto.listingId() == null && dto.roomId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Cần cung cấp tin đăng hoặc phòng để đặt lịch xem");
        }

        RoomListing listing = null;
        if (dto.listingId() != null) {
            listing = listingRepository.findById(dto.listingId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy tin đăng"));
        } else if (dto.roomId() != null) {
            listing = listingRepository.findByRoomId(dto.roomId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy phòng tương ứng"));
        }

        if (listing != null && !"ACTIVE".equals(listing.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tin đăng phòng này hiện không còn mở");
        }

        if (dto.appointmentDate().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thời gian hẹn phải ở tương lai");
        }

        Appointment appointment = new Appointment();
        appointment.setCustomerId(customerId);
        appointment.setStaffId(null); // No staff assigned yet - waiting for SALE confirmation
        appointment.setStatus("SCHEDULED");
        appointment.setAppointmentDate(dto.appointmentDate());
        appointment.setCreatedAt(Instant.now());

        if (listing != null) {
            appointment.setBuildingId(listing.getBuildingId());
            appointment.setRoomId(listing.getRoomId());
        }

        Appointment saved = appointmentRepository.save(appointment);

        // Publish notification to all SALE/MANAGER of this building
        publisher.publishAppointmentScheduled(new AppointmentCreatedPayload(
                saved.getId(),
                null, // no rentalRequest for self-booking
                customerId,
                saved.getStaffId(),
                saved.getBuildingId(),
                saved.getAppointmentDate()
        ));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> getMyAppointments(Long customerId, Pageable pageable) {
        Page<Appointment> page = appointmentRepository.findByCustomerId(customerId, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> searchAppointments(
            Long staffId, Long buildingId, String status, boolean unread,
            Instant fromTime, Instant toTime, Pageable pageable) {
        String normalizedStatus = status != null && !status.isBlank() ? status.trim().toUpperCase() : null;
        List<Long> allowedBuildings = buildingScope.allowedBuildingIdsOrNull();
        // Bound cứng thay vì tham số null: PostgreSQL không suy ra được kiểu của tham số
        // Instant chỉ xuất hiện trong điều kiện "IS NULL" (SQLState 42P18).
        Instant from = fromTime != null ? fromTime : MIN_TIME;
        Instant to = toTime != null ? toTime : MAX_TIME;

        if (allowedBuildings != null) {
            if (buildingId != null && !allowedBuildings.contains(buildingId)) {
                throw new AccessDeniedException("Nhân viên chưa được phân công tòa nhà này");
            }
            if (buildingId == null) {
                if (allowedBuildings.isEmpty()) {
                    return PageResponse.of(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0);
                }
                Page<Appointment> page = appointmentRepository.searchIn(
                        allowedBuildings, staffId, null, normalizedStatus, unread, from, to, pageable);
                return PageResponse.from(page.map(this::toResponse));
            }
        }

        Page<Appointment> page = appointmentRepository.search(
                staffId, buildingId, normalizedStatus, unread, from, to, pageable);
        return PageResponse.from(page.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public AppointmentResponse getById(Long id, Long userId, String userType) {
        Appointment appointment = findAppointment(id);
        if ("CUSTOMER".equals(userType)) {
            if (!appointment.getCustomerId().equals(userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền xem lịch hẹn này");
            }
        } else {
            buildingScope.assertAssigned(appointment.getBuildingId());
        }
        return toResponse(appointment);
    }

    @Transactional
    public AppointmentResponse updateStatus(Long id, UpdateAppointmentStatusDto dto) {
        Appointment appointment = findAppointment(id);
        buildingScope.assertAssigned(appointment.getBuildingId());

        String targetStatus = dto.status().trim().toUpperCase();
        if (!STAFF_TARGET_STATUSES.contains(targetStatus)) {
            throw new BusinessException("INVALID_STATUS",
                    "Trạng thái hợp lệ gồm: CONFIRMED, VIEWED, CANCELLED", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        assertTransitionAllowed(appointment.getStatus(), targetStatus);

        appointment.setStatus(targetStatus);
        return saveAndPublish(appointment);
    }

    @Transactional
    public AppointmentResponse markRead(Long id) {
        Appointment appointment = findAppointment(id);
        buildingScope.assertAssigned(appointment.getBuildingId());
        if (appointment.getReadAt() == null) {
            appointment.setReadAt(Instant.now());
            appointmentRepository.save(appointment);
        }
        return toResponse(appointment);
    }

    @Transactional
    public void cancelByCustomer(Long id, Long customerId) {
        Appointment appointment = findAppointment(id);

        if (!appointment.getCustomerId().equals(customerId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền hủy lịch hẹn này");
        }
        assertTransitionAllowed(appointment.getStatus(), "CANCELLED");

        appointment.setStatus("CANCELLED");
        saveAndPublish(appointment);
    }

    private AppointmentResponse saveAndPublish(Appointment appointment) {
        Appointment saved = appointmentRepository.save(appointment);
        publisher.publishAppointmentStatusChanged(new AppointmentStatusChangedPayload(
                saved.getId(),
                saved.getCustomerId(),
                saved.getStaffId(),
                saved.getBuildingId(),
                saved.getStatus()
        ));
        return toResponse(saved);
    }

    private static void assertTransitionAllowed(String current, String target) {
        Set<String> allowed = TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            throw new BusinessException("INVALID_STATUS",
                    "Không thể chuyển lịch hẹn từ \"" + statusLabel(current) + "\" sang \"" + statusLabel(target) + "\"",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "SCHEDULED" -> "Đã đặt lịch";
            case "CONFIRMED" -> "Đã xác nhận";
            case "VIEWED" -> "Đã xem phòng";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }

    private Appointment findAppointment(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn ID " + id));
    }

    private AppointmentResponse toResponse(Appointment a) {
        return new AppointmentResponse(
                a.getId(),
                a.getRentalRequestId(),
                a.getCustomerId(),
                a.getStaffId(),
                a.getBuildingId(),
                a.getAppointmentDate(),
                a.getStatus(),
                a.getCreatedAt(),
                a.getReadAt(),
                a.getRoomId()
        );
    }
}
