package com.rental.chat.service;

import com.rental.chat.client.ContractClient;
import com.rental.chat.client.ListingBookingClient;
import com.rental.chat.client.UserClient;
import com.rental.chat.dto.ContactResponse;
import com.rental.chat.dto.CreateConversationRequest;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.security.HeaderUser;
import feign.FeignException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Xac thuc quyen tham gia hoi thoai dua tren du lieu nghiep vu thuc te
 * (don ung tuyen, lich xem, don thue, hop dong) thay vi tin tuong id tu client.
 */
@Component
@RequiredArgsConstructor
public class ChatContextResolver {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final ListingBookingClient listing;
    private final ContractClient contracts;
    private final UserClient users;

    public ConversationContext resolve(HeaderUser user, CreateConversationRequest request) {
        String type = normalize(request.contextType());
        Long userId = Long.parseLong(user.getUserId());
        return switch (ChatContextType.valueOf(type)) {
            case ROOMMATE_APPLICATION -> roommate(userId, request.contextId());
            case ROOMMATE_POST -> roommatePost(user, userId, request.contextId());
            case LISTING -> listing(user, userId, request);
            case VIEWING -> viewing(user, userId, request);
            case BOOKING -> booking(user, userId, request);
            case BUILDING_MANAGER -> buildingManager(user, userId, request);
            case STAFF -> staff(user, userId, request);
        };
    }

    public List<ContactResponse> contacts(HeaderUser user, String contextType, Long contextId) {
        if (!isCustomer(user)) {
            return List.of();
        }
        Long userId = Long.parseLong(user.getUserId());
        String type = normalize(contextType);
        return switch (ChatContextType.valueOf(type)) {
            case ROOMMATE_APPLICATION, ROOMMATE_POST, STAFF -> List.of();
            case LISTING -> {
                ListingBookingClient.ListingContext ctx = call(
                        () -> listing.listing(requireContextId(contextId, "tin đăng")),
                        "Không tìm thấy tin đăng");
                yield saleContact(ctx.buildingId(), null);
            }
            case VIEWING -> {
                ListingBookingClient.AppointmentContext ctx = call(
                        () -> listing.appointment(requireContextId(contextId, "lịch xem phòng")),
                        "Không tìm thấy lịch xem phòng");
                if (!ctx.customerId().equals(userId)) {
                    throw forbidden("Bạn không có lịch xem phòng này");
                }
                yield saleContact(ctx.buildingId(), ctx.staffId());
            }
            case BOOKING -> {
                ListingBookingClient.RentalRequestContext ctx = call(
                        () -> listing.rentalRequest(requireContextId(contextId, "đơn thuê")),
                        "Không tìm thấy đơn thuê");
                if (!ctx.customerId().equals(userId)) {
                    throw forbidden("Bạn không có đơn thuê này");
                }
                yield saleContact(ctx.buildingId(), ctx.verifiedBy());
            }
            case BUILDING_MANAGER -> {
                ContractClient.ActiveContract contract = activeContract(userId);
                yield contract == null ? List.of() : managerContacts(contract.buildingId());
            }
        };
    }

    private ConversationContext roommate(Long userId, Long applicationId) {
        ListingBookingClient.RoommateContext ctx = call(
                () -> listing.roommateApplication(requireContextId(applicationId, "đơn ứng tuyển ở ghép")),
                "Không tìm thấy đơn ứng tuyển ở ghép");
        boolean owner = ctx.ownerCustomerId().equals(userId);
        boolean applicant = ctx.applicantCustomerId().equals(userId);
        if (!owner && !applicant) {
            throw forbidden("Bạn không thuộc hội thoại ở ghép này");
        }
        ContextParticipant self = customerParticipant(userId, owner ? "OWNER" : "APPLICANT");
        ContextParticipant peer = customerParticipant(
                owner ? ctx.applicantCustomerId() : ctx.ownerCustomerId(),
                owner ? "APPLICANT" : "OWNER");
        String label = join("Ở ghép", ctx.postTitle(), place(ctx.roomNumber(), ctx.buildingName()));
        return context("ROOMMATE_APPLICATION", ctx.applicationId(), "ROOMMATE_APPLICATION:"
                + ctx.applicationId(), label, ctx.buildingId(), ctx.roomId(), self, peer);
    }

    private ConversationContext roommatePost(HeaderUser user, Long userId, Long postId) {
        if (!isCustomer(user)) {
            throw forbidden("Chỉ khách hàng mới nhắn chủ phòng ở ghép");
        }
        ListingBookingClient.RoommatePostContext ctx = call(
                () -> listing.roommatePost(requireContextId(postId, "tin ở ghép")),
                "Không tìm thấy tin ở ghép");
        if (ctx.ownerCustomerId().equals(userId)) {
            throw forbidden("Đây là tin đăng của bạn");
        }
        ContextParticipant self = customerParticipant(userId, "INTERESTED");
        ContextParticipant peer = customerParticipant(ctx.ownerCustomerId(), "OWNER");
        String label = join("Ở ghép", ctx.postTitle(), place(ctx.roomNumber(), ctx.buildingName()));
        return context("ROOMMATE_POST", ctx.postId(), "ROOMMATE_POST:" + ctx.postId() + ":" + userId,
                label, ctx.buildingId(), ctx.roomId(), self, peer);
    }

    private ConversationContext listing(HeaderUser user, Long userId, CreateConversationRequest request) {
        if (!isCustomer(user)) {
            throw forbidden("Chỉ khách hàng mới nhắn SALE tư vấn");
        }
        ListingBookingClient.ListingContext ctx = call(
                () -> listing.listing(requireContextId(request.contextId(), "tin đăng")),
                "Không tìm thấy tin đăng");
        UserClient.StaffSummary sale = requireSale(ctx.buildingId(), null);
        ContextParticipant self = customerParticipant(userId, "CUSTOMER");
        ContextParticipant peer = staffParticipant(sale.id(), "SALE");
        String label = join("Tư vấn thuê", place(ctx.roomNumber(), ctx.buildingName()), ctx.title());
        return context("LISTING", ctx.listingId(), saleKey(ctx.buildingId(), sale.id(), userId),
                label, ctx.buildingId(), ctx.roomId(), self, peer);
    }

    private ConversationContext viewing(HeaderUser user, Long userId, CreateConversationRequest request) {
        ListingBookingClient.AppointmentContext ctx = call(
                () -> listing.appointment(request.contextId()), "Không tìm thấy lịch xem phòng");
        if (isCustomer(user) && !ctx.customerId().equals(userId)) {
            throw forbidden("Bạn không có lịch xem phòng này");
        }
        UserClient.StaffSummary sale = requireSale(ctx.buildingId(), ctx.staffId());
        if (!isCustomer(user) && !sale.id().equals(userId)) {
            throw forbidden("Bạn không phải SALE phụ trách tòa nhà này");
        }
        ContextParticipant self = isCustomer(user)
                ? customerParticipant(userId, "CUSTOMER")
                : staffParticipant(userId, "SALE");
        ContextParticipant peer = isCustomer(user)
                ? staffParticipant(sale.id(), "SALE")
                : customerParticipant(ctx.customerId(), "CUSTOMER");
        String when = ctx.appointmentDate() == null ? null : DATE_TIME.format(ctx.appointmentDate());
        String label = join("Xem phòng", place(ctx.roomNumber(), ctx.buildingName()), when);
        return context("VIEWING", ctx.appointmentId(), saleKey(ctx.buildingId(), sale.id(), ctx.customerId()),
                label, ctx.buildingId(), ctx.roomId(), self, peer);
    }

    private ConversationContext booking(HeaderUser user, Long userId, CreateConversationRequest request) {
        ListingBookingClient.RentalRequestContext ctx = call(
                () -> listing.rentalRequest(request.contextId()), "Không tìm thấy đơn thuê");
        if (isCustomer(user) && !ctx.customerId().equals(userId)) {
            throw forbidden("Bạn không có đơn thuê này");
        }
        UserClient.StaffSummary sale = requireSale(ctx.buildingId(), ctx.verifiedBy());
        if (!isCustomer(user) && !sale.id().equals(userId)) {
            throw forbidden("Bạn không phải SALE phụ trách tòa nhà này");
        }
        ContextParticipant self = isCustomer(user)
                ? customerParticipant(userId, "CUSTOMER")
                : staffParticipant(userId, "SALE");
        ContextParticipant peer = isCustomer(user)
                ? staffParticipant(sale.id(), "SALE")
                : customerParticipant(ctx.customerId(), "CUSTOMER");
        String label = join("Đơn thuê", place(ctx.roomNumber(), ctx.buildingName()));
        return context("BOOKING", ctx.rentalRequestId(), saleKey(ctx.buildingId(), sale.id(), ctx.customerId()),
                label, ctx.buildingId(), ctx.roomId(), self, peer);
    }

    private ConversationContext buildingManager(HeaderUser user, Long userId, CreateConversationRequest request) {
        Long customerId;
        Long staffId;
        Long buildingId;
        String roomNumber;
        String buildingName;
        ContextParticipant self;
        ContextParticipant peer;

        if (isCustomer(user)) {
            customerId = userId;
            ContractClient.ActiveContract contract = activeContract(customerId);
            if (contract == null) {
                throw forbidden("Bạn cần có hợp đồng đang hiệu lực để nhắn quản lý tòa");
            }
            buildingId = contract.buildingId();
            roomNumber = contract.roomNumber();
            buildingName = contract.buildingName();
            staffId = request.staffId();
            if (staffId == null) {
                throw validation("Vui lòng chọn quản lý tòa nhà");
            }
            requireManager(buildingId, staffId);
            self = customerParticipant(customerId, "CUSTOMER");
            peer = staffParticipant(staffId, "MANAGER");
        } else {
            if (request.customerId() == null) {
                throw validation("Thiếu khách hàng cần nhắn tin");
            }
            customerId = request.customerId();
            ContractClient.ActiveContract contract = activeContract(customerId);
            if (contract == null) {
                throw notFound("Khách hàng không có hợp đồng đang hiệu lực");
            }
            if (!contract.buildingId().equals(request.contextId())) {
                throw forbidden("Khách hàng không thuê tòa nhà này");
            }
            buildingId = contract.buildingId();
            roomNumber = contract.roomNumber();
            buildingName = contract.buildingName();
            staffId = userId;
            requireManager(buildingId, staffId);
            self = staffParticipant(staffId, "MANAGER");
            peer = customerParticipant(customerId, "CUSTOMER");
        }

        String label = join("Quản lý tòa", place(roomNumber, buildingName));
        String dedupKey = "BUILDING_MANAGER:" + buildingId + ":" + staffId + ":" + customerId;
        return context("BUILDING_MANAGER", buildingId, dedupKey, label, buildingId, null, self, peer);
    }

    private ConversationContext staff(HeaderUser user, Long userId, CreateConversationRequest request) {
        if (isCustomer(user)) {
            throw forbidden("Chỉ nhân viên mới có thể nhắn tin nội bộ");
        }
        Long peerStaffId = request.staffId() != null ? request.staffId() : request.contextId();
        if (peerStaffId == null) {
            throw validation("Thiếu thông tin nhân viên cần nhắn tin");
        }
        if (peerStaffId.equals(userId)) {
            throw validation("Không thể tự nhắn tin cho chính mình");
        }
        UserClient.StaffSummary peerStaff = users.staff(List.of(peerStaffId)).stream()
                .filter(item -> item.id().equals(peerStaffId))
                .findFirst()
                .orElseThrow(() -> notFound("Không tìm thấy nhân viên"));

        UserClient.StaffSummary selfStaff = users.staff(List.of(userId)).stream()
                .filter(item -> item.id().equals(userId))
                .findFirst()
                .orElse(null);

        String selfRole = selfStaff != null && selfStaff.position() != null ? selfStaff.position() : "STAFF";
        String peerRole = peerStaff.position() != null ? peerStaff.position() : "STAFF";

        ContextParticipant self = staffParticipant(userId, selfRole);
        ContextParticipant peer = staffParticipant(peerStaffId, peerRole);

        Long minId = Math.min(userId, peerStaffId);
        Long maxId = Math.max(userId, peerStaffId);
        String dedupKey = "STAFF:" + minId + ":" + maxId;
        String label = "Trao đổi nội bộ";

        return context("STAFF", peerStaffId, dedupKey, label, null, null, self, peer);
    }

    private ContractClient.ActiveContract activeContract(Long customerId) {
        try {
            return contracts.activeContract(customerId);
        } catch (FeignException.NotFound ex) {
            return null;
        }
    }

    private UserClient.StaffSummary requireManager(Long buildingId, Long staffId) {
        return staffOf(buildingId).stream()
                .filter(item -> item.id().equals(staffId))
                .filter(this::isManager)
                .findFirst()
                .orElseThrow(() -> forbidden("Nhân viên không phải quản lý của tòa nhà này"));
    }

    private UserClient.StaffSummary requireSale(Long buildingId, Long preferredStaffId) {
        return saleOf(buildingId, preferredStaffId)
                .orElseThrow(() -> new BusinessException("SALE_NOT_ASSIGNED",
                        "Tòa nhà chưa có nhân viên kinh doanh phụ trách.",
                        HttpStatus.UNPROCESSABLE_ENTITY));
    }

    private Optional<UserClient.StaffSummary> saleOf(Long buildingId, Long preferredStaffId) {
        List<UserClient.StaffSummary> staff = staffOf(buildingId);
        if (preferredStaffId != null) {
            Optional<UserClient.StaffSummary> preferred = staff.stream()
                    .filter(item -> item.id().equals(preferredStaffId))
                    .filter(this::isSale)
                    .findFirst();
            if (preferred.isPresent()) {
                return preferred;
            }
        }
        return staff.stream()
                .filter(this::isSale)
                .min(Comparator.comparing(UserClient.StaffSummary::id));
    }

    private List<UserClient.StaffSummary> staffOf(Long buildingId) {
        return buildingId == null ? List.of() : users.staffByBuilding(buildingId);
    }

    private boolean isSale(UserClient.StaffSummary staff) {
        return staff.position() != null && "SALE".equalsIgnoreCase(staff.position().trim());
    }

    private boolean isManager(UserClient.StaffSummary staff) {
        if (Boolean.TRUE.equals(staff.isAdmin())) {
            return true;
        }
        String position = staff.position();
        return position != null
                && ("QUAN_LY".equalsIgnoreCase(position.trim()) || "ADMIN".equalsIgnoreCase(position.trim()));
    }

    private List<ContactResponse> saleContact(Long buildingId, Long preferredStaffId) {
        return saleOf(buildingId, preferredStaffId)
                .map(sale -> List.of(contactOf(sale.id())))
                .orElseGet(List::of);
    }

    private List<ContactResponse> managerContacts(Long buildingId) {
        return staffOf(buildingId).stream()
                .filter(this::isManager)
                .map(item -> new ContactResponse(item.id(), nameOf(item.fullName(), "Quản lý"),
                        item.position(), true, false))
                .sorted(Comparator.comparing(ContactResponse::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private ContactResponse contactOf(Long staffId) {
        UserClient.StaffSummary staff = users.staff(List.of(staffId)).stream()
                .filter(item -> item.id().equals(staffId))
                .findFirst()
                .orElse(null);
        if (staff == null) {
            return new ContactResponse(staffId, "Nhân viên", null, false, true);
        }
        return new ContactResponse(staffId, nameOf(staff.fullName(), "Nhân viên"), staff.position(),
                isManager(staff), true);
    }

    private ContextParticipant customerParticipant(Long id, String role) {
        return new ContextParticipant(id, "CUSTOMER", role, customerName(id));
    }

    private ContextParticipant staffParticipant(Long id, String role) {
        return new ContextParticipant(id, "STAFF", role, staffName(id));
    }

    private String customerName(Long id) {
        try {
            return users.customers(List.of(id)).stream()
                    .filter(item -> item.id().equals(id))
                    .findFirst()
                    .map(item -> nameOf(item.fullName(), "Khách hàng"))
                    .orElse("Khách hàng");
        } catch (Exception ex) {
            return "Khách hàng";
        }
    }

    private String staffName(Long id) {
        try {
            return users.staff(List.of(id)).stream()
                    .filter(item -> item.id().equals(id))
                    .findFirst()
                    .map(item -> nameOf(item.fullName(), "Nhân viên"))
                    .orElse("Nhân viên");
        } catch (Exception ex) {
            return "Nhân viên";
        }
    }

    private static String saleKey(Long buildingId, Long saleId, Long customerId) {
        return "BUILDING_SALE:" + buildingId + ":" + saleId + ":" + customerId;
    }

    private static ConversationContext context(String type, Long contextId, String dedupKey, String label,
            Long buildingId, Long roomId, ContextParticipant self, ContextParticipant peer) {
        return new ConversationContext(type, contextId, dedupKey, truncate(label, 255), buildingId, roomId,
                self, peer);
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw validation("Thiếu loại hội thoại");
        }
        try {
            return ChatContextType.valueOf(raw.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            throw validation("Loại hội thoại không hợp lệ");
        }
    }

    private static Long requireContextId(Long contextId, String name) {
        if (contextId == null) {
            throw validation("Thiếu mã " + name);
        }
        return contextId;
    }

    private static boolean isCustomer(HeaderUser user) {
        return "CUSTOMER".equalsIgnoreCase(user.getUserType());
    }

    private static String place(String roomNumber, String buildingName) {
        String room = roomNumber == null || roomNumber.isBlank() ? null : "Phòng " + roomNumber;
        String building = buildingName == null || buildingName.isBlank() ? null : buildingName;
        if (room != null && building != null) {
            return room + " - " + building;
        }
        return room != null ? room : building;
    }

    private static String join(String... parts) {
        return Stream.of(parts)
                .filter(part -> part != null && !part.isBlank())
                .reduce((left, right) -> left + " · " + right)
                .orElse("");
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String nameOf(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static <T> T call(Supplier<T> supplier, String notFoundMessage) {
        try {
            T result = supplier.get();
            if (result == null) {
                throw notFound(notFoundMessage);
            }
            return result;
        } catch (FeignException.NotFound ex) {
            throw notFound(notFoundMessage);
        }
    }

    private static BusinessException forbidden(String message) {
        return new BusinessException(ErrorCode.FORBIDDEN, message);
    }

    private static BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    private static BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }
}
