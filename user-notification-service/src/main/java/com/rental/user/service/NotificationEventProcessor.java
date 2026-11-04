package com.rental.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rental.user.entity.Customer;
import com.rental.user.entity.Notification;
import com.rental.user.entity.ProcessedEvent;
import com.rental.user.entity.Staff;
import com.rental.user.repository.CustomerRepository;
import com.rental.user.repository.DeviceTokenRepository;
import com.rental.user.repository.NotificationRepository;
import com.rental.user.repository.ProcessedEventRepository;
import com.rental.user.repository.StaffBuildingRepository;
import com.rental.user.repository.StaffRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationEventProcessor {
    private final ObjectMapper mapper;
    private final ProcessedEventRepository processed;
    private final CustomerRepository customers;
    private final StaffRepository staff;
    private final StaffBuildingRepository assignments;
    private final NotificationRepository notifications;
    private final DeviceTokenRepository devices;
    private final ApplicationEventPublisher publisher;

    public NotificationEventProcessor(ObjectMapper mapper, ProcessedEventRepository processed,
            CustomerRepository customers, StaffRepository staff, StaffBuildingRepository assignments,
            NotificationRepository notifications, DeviceTokenRepository devices, ApplicationEventPublisher publisher) {
        this.mapper = mapper; this.processed = processed; this.customers = customers; this.staff = staff;
        this.assignments = assignments; this.notifications = notifications; this.devices = devices; this.publisher = publisher;
    }

    @Transactional
    public void process(String raw) throws Exception {
        JsonNode envelope = mapper.readTree(raw);
        String eventType = text(envelope, "eventType");
        UUID eventId;
        try { eventId = UUID.fromString(text(envelope, "eventId")); }
        catch (Exception ex) { throw new IllegalArgumentException("eventId phải là UUID", ex); }
        if (processed.existsById(eventId)) return;
        JsonNode payload = envelope.path("payload");
        if ("AccountRegistered".equals(eventType)) {
            registerProfile(payload);
            processed.save(new ProcessedEvent(eventId));
            return;
        }
        NotificationSpec spec = spec(eventType, payload);
        if (spec == null) return;
        Set<Long> recipients = recipients(spec, payload);
        for (Long recipientId : recipients) {
            Notification entity = new Notification();
            entity.setRecipientId(recipientId); entity.setType(eventType); entity.setTitle(title(eventType));
            entity.setContent(content(eventType, payload)); entity.setRefType(spec.refType());
            long refId = number(payload, spec.refField());
            entity.setRefId(refId > 0 ? refId : null);
            notifications.save(entity);
            String email = customers.findById(recipientId).map(Customer::getEmail)
                    .orElseGet(() -> staff.findById(recipientId).map(Staff::getEmail).orElse(null));
            List<String> tokens = devices.findByAccountId(recipientId).stream().map(d -> d.getToken()).toList();
            publisher.publishEvent(new NotificationDeliveryEvent(eventType, entity.getTitle(), entity.getContent(), email, tokens));
        }
        processed.save(new ProcessedEvent(eventId));
    }

    private void registerProfile(JsonNode p) {
        long id = number(p, "accountId");
        String type = text(p, "accountType");
        if (id < 1) throw new IllegalArgumentException("accountId phải là số dương");
        if ("CUSTOMER".equals(type)) {
            Customer person = customers.findById(id).orElseGet(Customer::new);
            person.setId(id); person.setFullName(optionalText(p, "fullName"));
            person.setPhone(optionalText(p, "phone")); person.setEmail(optionalText(p, "email"));
            customers.save(person);
        } else if ("STAFF".equals(type) || "ADMIN".equals(type)) {
            Staff person = staff.findById(id).orElseGet(Staff::new);
            person.setId(id); person.setFullName(optionalText(p, "fullName"));
            person.setPhone(optionalText(p, "phone")); person.setEmail(optionalText(p, "email"));
            person.setAdmin("ADMIN".equals(type));
            // auth_db la nguon su that ve role; FE loc ky thuat vien theo position nen seed
            // position tu role luc tao ho so, ADMIN co the sua lai thanh chuc danh hien thi.
            if (person.getPosition() == null || person.getPosition().isBlank()) {
                person.setPosition(primaryRole(p.path("roles")));
            }
            staff.save(person);
        } else throw new IllegalArgumentException("accountType không được hỗ trợ");
    }

    private Set<Long> recipients(NotificationSpec spec, JsonNode p) {
        Set<Long> result = new LinkedHashSet<>();
        switch (spec.audience()) {
            case STAFF_BUILDING -> addStaffAndAdmins(result, number(p, "buildingId"));
            case CUSTOMER -> add(result, number(p, "customerId"));
            case CUSTOMER_AND_STAFF -> { add(result, number(p, "customerId")); add(result, number(p, "staffId")); }
            case OWNER -> add(result, number(p, "ownerCustomerId"));
            case APPLICANT -> add(result, number(p, "applicantCustomerId"));
            case TENANTS -> addArray(result, p.path("tenantIds"));
            case CUSTOMER_AND_OWNER -> { add(result, number(p, "customerId")); add(result, number(p, "ownerCustomerId")); }
            case OWNER_AND_APPLICANT -> { add(result, number(p, "ownerCustomerId")); add(result, number(p, "applicantCustomerId")); }
            case STAFF -> add(result, number(p, "staffId"));
            case CUSTOMER_STAFF_BUILDING -> {
                add(result, number(p, "customerId")); addStaffAndAdmins(result, number(p, "buildingId"));
            }
        }
        return result;
    }

    private void addStaffAndAdmins(Set<Long> result, long buildingId) {
        if (buildingId <= 0) return;
        assignments.findByIdBuildingId(buildingId).forEach(a -> result.add(a.getId().getStaffId()));
        staff.findByAdminTrue().forEach(s -> result.add(s.getId()));
    }

    private static void add(Set<Long> result, long id) { if (id > 0) result.add(id); }
    private static void addArray(Set<Long> result, JsonNode array) {
        if (array != null && array.isArray()) array.forEach(n -> { if (n.canConvertToLong() && n.asLong() > 0) result.add(n.asLong()); });
    }

    private static NotificationSpec spec(String event, JsonNode p) {
        return switch (event) {
            case "RentalRequestCreated", "MoveOutRequested", "ReportCreated", "MeterReadingMissing", "ContractRenewalRequested",
                    "MeterReadingHandoverDue" ->
                    new NotificationSpec(Audience.STAFF_BUILDING, ref(event), idField(event));
            case "MeterReadingReminder" -> new NotificationSpec(Audience.STAFF_BUILDING, "Building", "buildingId");
            case "InvoiceOverdue" -> new NotificationSpec(Audience.CUSTOMER_STAFF_BUILDING, "Invoice", "invoiceId");
            case "RentalRequestAccepted", "RentalRequestRejected", "PaymentReceived", "InvoiceIssued", "ReportRejected",
                    "MaintenanceCompleted", "TenantRemoved", "ContractRenewalApproved", "ContractRenewalRejected" ->
                    new NotificationSpec(Audience.CUSTOMER, ref(event), idField(event));
            case "AppointmentScheduled", "AppointmentStatusChanged", "MaintenanceRequestAssigned" ->
                    new NotificationSpec(Audience.CUSTOMER_AND_STAFF, ref(event), idField(event));
            case "RoommateApplicationCreated" -> new NotificationSpec(Audience.OWNER, "RoommateApplication", "applicationId");
            case "RoommateApplicationRejected" -> new NotificationSpec(Audience.APPLICANT, "RoommateApplication", "applicationId");
            case "TenantAdded" -> new NotificationSpec(Audience.CUSTOMER_AND_OWNER, "Contract", "contractId");
            case "TenantAddRejected" -> new NotificationSpec(Audience.OWNER_AND_APPLICANT, "RoommateApplication", "applicationId");
            case "ContractSigned", "ContractRenewed", "MoveOutApproved" ->
                    new NotificationSpec(Audience.TENANTS, "Contract", "contractId");
            case "MaintenanceScheduleDue" -> new NotificationSpec(Audience.STAFF, "MaintenanceSchedule", "scheduleId");
            default -> null;
        };
    }

    private static String ref(String event) {
        if (event.equals("MeterReadingHandoverDue")) return "Contract";
        if (event.contains("RentalRequest")) return "RentalRequest";
        if (event.startsWith("Appointment")) return "Appointment";
        if (event.startsWith("RoommateApplication")) return "RoommateApplication";
        if (event.startsWith("MoveOut")) return "MoveOutRequest";
        if (event.startsWith("Contract") || event.startsWith("Tenant")) return "Contract";
        if (event.startsWith("Invoice") || event.equals("PaymentReceived")) return "Invoice";
        if (event.startsWith("Report")) return "Report";
        if (event.startsWith("Maintenance")) return "MaintenanceRequest";
        return "Room";
    }
    private static String idField(String event) {
        if (event.equals("MeterReadingHandoverDue")) return "contractId";
        if (event.startsWith("RentalRequest")) return "rentalRequestId";
        if (event.startsWith("MoveOut")) return "moveOutRequestId";
        if (event.startsWith("Report")) return "reportId";
        if (event.equals("MeterReadingMissing")) return "roomId";
        if (event.startsWith("Appointment")) return "appointmentId";
        if (event.startsWith("RoommateApplication") || event.equals("TenantAddRejected")) return "applicationId";
        if (event.equals("PaymentReceived")) return "invoiceId";
        if (event.startsWith("Invoice")) return "invoiceId";
        if (event.startsWith("MaintenanceSchedule")) return "scheduleId";
        if (event.startsWith("Maintenance")) return "maintenanceRequestId";
        if (event.startsWith("Tenant")) return "contractId";
        return "contractId";
    }
    private static String title(String event) {
        return switch (event) {
            case "RentalRequestCreated" -> "Có yêu cầu thuê phòng mới";
            case "RentalRequestAccepted" -> "Hợp đồng thuê phòng đã được tạo nháp";
            case "RentalRequestRejected" -> "Yêu cầu thuê phòng chưa được chấp nhận";
            case "AppointmentScheduled" -> "Lịch xem phòng mới";
            case "AppointmentStatusChanged" -> "Lịch xem phòng đã thay đổi";
            case "RoommateApplicationCreated" -> "Có người đăng ký ở ghép";
            case "RoommateApplicationRejected" -> "Đơn ở ghép đã bị từ chối";
            case "ContractSigned" -> "Hợp đồng thuê phòng đã được ký";
            case "ContractRenewed" -> "Hợp đồng thuê phòng đã được gia hạn";
            case "TenantAdded" -> "Đã thêm người ở vào hợp đồng";
            case "TenantRemoved" -> "Người thuê đã rời hợp đồng";
            case "MoveOutRequested" -> "Có yêu cầu trả phòng";
            case "MoveOutApproved" -> "Yêu cầu trả phòng đã được duyệt";
            case "ContractRenewalRequested" -> "Có yêu cầu gia hạn hợp đồng mới";
            case "ContractRenewalApproved" -> "Yêu cầu gia hạn hợp đồng đã được duyệt";
            case "ContractRenewalRejected" -> "Yêu cầu gia hạn hợp đồng bị từ chối";
            case "InvoiceIssued" -> "Hóa đơn mới đã được phát hành";
            case "PaymentReceived" -> "Đã ghi nhận thanh toán";
            case "InvoiceOverdue" -> "Hóa đơn đã quá hạn thanh toán";
            case "MeterReadingMissing" -> "Phòng còn thiếu chỉ số công tơ";
            case "MeterReadingHandoverDue" -> "Cần ghi chỉ số điện nước bàn giao hôm nay";
            case "MeterReadingReminder" -> "Nhắc nhập chỉ số điện nước cuối tháng";

            case "ReportCreated" -> "Có báo hư hỏng mới";
            case "ReportRejected" -> "Báo cáo hư hỏng chưa được duyệt";
            case "MaintenanceRequestAssigned" -> "Phiếu bảo trì đã được phân công";
            case "MaintenanceCompleted" -> "Công việc bảo trì đã hoàn tất";
            case "MaintenanceScheduleDue" -> "Đến lịch bảo trì định kỳ";
            case "TenantAddRejected" -> "Đơn thêm người ở đã bị từ chối";
            default -> "Cập nhật từ hệ thống cho thuê phòng";
        };
    }
    private static String content(String event, JsonNode p) {
        return switch (event) {
            case "RentalRequestCreated" -> "Mã yêu cầu #" + value(p, "rentalRequestId") + " đang chờ xử lý.";
            case "RentalRequestAccepted" -> "Hợp đồng thuê phòng đã được tạo nháp! Vui lòng vào xem và chuyển cọc trong 2 ngày để hoàn tất ký kết.";
            case "RentalRequestRejected" -> "Yêu cầu #" + value(p, "rentalRequestId") + " đã bị từ chối. " + value(p, "reason");
            case "AppointmentScheduled" -> "Lịch xem phòng: " + dateValue(p, "appointmentDate") + ".";
            case "AppointmentStatusChanged" -> "Trạng thái lịch xem phòng: " + value(p, "status") + ".";
            case "RoommateApplicationCreated" -> "Có đơn đăng ký bài ở ghép #" + value(p, "postId") + ".";
            case "RoommateApplicationRejected" -> "Đơn đăng ký ở ghép #" + value(p, "applicationId") + " đã bị từ chối.";
            case "InvoiceIssued" -> "Hóa đơn tháng " + value(p, "month") + "/" + value(p, "year") + ", hạn thanh toán " + dateValue(p, "dueDate") + ".";
            case "InvoiceOverdue" -> "Hóa đơn tháng " + value(p, "month") + "/" + value(p, "year") + " đã quá hạn.";
            case "PaymentReceived" -> "Đã ghi nhận thanh toán " + value(p, "amountPaid") + " đồng.";
            case "ReportCreated" -> "Báo hư phòng #" + value(p, "roomId") + ": " + value(p, "title") + ".";
            case "ReportRejected" -> "Báo cáo hư hỏng bị từ chối. " + value(p, "reason");
            case "MaintenanceRequestAssigned" -> "Thợ dự kiến đến lúc " + dateValue(p, "scheduledAt") + ".";
            case "MaintenanceCompleted" -> "Phiếu bảo trì đã hoàn tất; chi phí " + value(p, "cost") + " đồng.";
            case "MaintenanceScheduleDue" -> "Lịch " + value(p, "title") + " đến hạn " + dateValue(p, "nextDate") + ".";
            case "ContractSigned" -> "Hợp đồng #" + value(p, "contractId") + " đã có hiệu lực.";
            case "ContractRenewed" -> "Hợp đồng được gia hạn đến " + dateValue(p, "newEndDate") + ".";
            case "MoveOutRequested" -> "Ngày dự kiến trả phòng: " + dateValue(p, "expectedMoveOutDate") + ".";
            case "MoveOutApproved" -> "Yêu cầu trả phòng đã được duyệt.";
            case "ContractRenewalRequested" -> "Khách thuê phòng " + value(p, "roomNumber") + " (" + value(p, "buildingName") + ") đã gửi yêu cầu gia hạn hợp đồng 1 năm.";
            case "ContractRenewalApproved" -> "Yêu cầu gia hạn hợp đồng phòng " + value(p, "roomNumber") + " (" + value(p, "buildingName") + ") đã được duyệt. Ban quản lý đang soạn hợp đồng mới để gửi bạn ký.";
            case "ContractRenewalRejected" -> "Yêu cầu gia hạn hợp đồng phòng " + value(p, "roomNumber") + " (" + value(p, "buildingName") + ") đã bị từ chối. " + value(p, "reason");
            case "MeterReadingMissing" -> "Phòng " + roomLabel(p) + " chưa có chỉ số công tơ tháng "
                    + value(p, "month") + "/" + value(p, "year") + ".";
            case "MeterReadingHandoverDue" -> "Phòng " + roomLabel(p) + buildingSuffix(p)
                    + " nhận bàn giao ngày " + dateValue(p, "moveInDate")
                    + ". Vui lòng ghi chỉ số điện nước bàn giao trước khi giao phòng.";
            case "MeterReadingReminder" -> "Tòa " + buildingLabel(p) + " còn "
                    + value(p, "roomCount") + " phòng chưa nhập chỉ số tháng "
                    + value(p, "month") + "/" + value(p, "year") + ": " + stringList(p, "roomNumbers") + ".";
            case "TenantAddRejected" -> "Không thể thêm người ở: " + value(p, "reason") + ".";
            default -> "Có cập nhật mới liên quan đến " + ref(event) + " #" + value(p, idField(event)) + ".";
        };
    }
    private static String value(JsonNode p, String field) { String v = optionalText(p, field); return v == null ? "" : v; }
    /** Uu tien so phong de nguoi nhan doc duoc; chi dung ID khi su kien cu khong kem so phong. */
    private static String roomLabel(JsonNode p) {
        String number = optionalText(p, "roomNumber");
        return number == null || number.isBlank() ? "#" + value(p, "roomId") : number;
    }
    private static String buildingLabel(JsonNode p) {
        String name = optionalText(p, "buildingName");
        return name == null || name.isBlank() ? "#" + value(p, "buildingId") : name;
    }
    private static String buildingSuffix(JsonNode p) {
        String name = optionalText(p, "buildingName");
        return name == null || name.isBlank() ? "" : " (" + name + ")";
    }
    private static String stringList(JsonNode p, String field) {
        JsonNode array = p.get(field);
        if (array == null || !array.isArray() || array.isEmpty()) return "";
        java.util.List<String> values = new java.util.ArrayList<>();
        array.forEach(n -> values.add(n.asText()));
        return String.join(", ", values);
    }
    /** Noi dung thong bao hien thi ngay/gio theo gio Viet Nam thay vi chuoi ISO tho. */
    private static String dateValue(JsonNode p, String field) {
        String raw = optionalText(p, field);
        if (raw == null || raw.isBlank()) return "";
        try {
            return java.time.OffsetDateTime.parse(raw)
                    .atZoneSameInstant(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (java.time.format.DateTimeParseException ignored) {
            // Thu tiep dang LocalDateTime / LocalDate.
        }
        try {
            return java.time.LocalDateTime.parse(raw)
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (java.time.format.DateTimeParseException ignored) {
            // Thu tiep dang LocalDate.
        }
        try {
            return java.time.LocalDate.parse(raw)
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (java.time.format.DateTimeParseException ignored) {
            return raw;
        }
    }
    private static String optionalText(JsonNode p, String field) {
        JsonNode n = p.get(field); return n == null || n.isNull() ? null : n.asText();
    }
    private static String text(JsonNode p, String field) {
        String v = optionalText(p, field); if (v == null || v.isBlank()) throw new IllegalArgumentException(field + " là bắt buộc"); return v;
    }
    private static long number(JsonNode p, String field) {
        JsonNode n = p.get(field); return n != null && n.canConvertToLong() ? n.asLong() : -1L;
    }
    /** Staff co the giu nhieu role; position hien thi chon theo thu tu uu tien phan quyen. */
    private static String primaryRole(JsonNode roles) {
        if (roles == null || !roles.isArray()) return null;
        java.util.Set<String> codes = new LinkedHashSet<>();
        roles.forEach(n -> codes.add(n.asText()));
        for (String preferred : List.of("ADMIN", "QUAN_LY", "KY_THUAT", "SALE")) {
            if (codes.contains(preferred)) return preferred;
        }
        return codes.stream().findFirst().orElse(null);
    }

    private enum Audience { STAFF_BUILDING, CUSTOMER, CUSTOMER_AND_STAFF, OWNER, APPLICANT, TENANTS,
        CUSTOMER_AND_OWNER, OWNER_AND_APPLICANT, STAFF, CUSTOMER_STAFF_BUILDING }
    private record NotificationSpec(Audience audience, String refType, String refField) { }
}
