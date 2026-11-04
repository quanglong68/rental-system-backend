package com.rental.user.service;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.security.HeaderUser;
import com.rental.user.dto.BuildingIdsRequest;
import com.rental.common.dto.PageResponse;
import com.rental.user.dto.StaffSummaryResponse;
import com.rental.user.dto.UpdateProfileRequest;
import com.rental.user.dto.UpdateStaffRequest;
import com.rental.user.dto.UserProfileResponse;
import com.rental.user.entity.Customer;
import com.rental.user.entity.Staff;
import com.rental.user.entity.StaffBuilding;
import com.rental.user.entity.StaffBuildingId;
import com.rental.user.repository.CustomerRepository;
import com.rental.user.repository.StaffBuildingRepository;
import com.rental.user.repository.StaffRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserProfileService {
    private final CustomerRepository customers;
    private final StaffRepository staff;
    private final StaffBuildingRepository assignments;

    public UserProfileService(CustomerRepository customers, StaffRepository staff,
            StaffBuildingRepository assignments) {
        this.customers = customers; this.staff = staff; this.assignments = assignments;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse me() {
        HeaderUser user = CurrentUser.require();
        Long id = CurrentUser.id();
        if ("CUSTOMER".equals(user.getUserType())) return customer(id);
        return staff(id);
    }

    public UserProfileResponse updateMe(UpdateProfileRequest request) {
        HeaderUser user = CurrentUser.require();
        Long id = CurrentUser.id();
        if ("CUSTOMER".equals(user.getUserType())) {
            Customer customer = customers.findById(id).orElseThrow(() -> notFound("Không tìm thấy hồ sơ"));
            customer.setFullName(request.fullName()); customer.setPhone(request.phone()); customer.setEmail(request.email());
            return profile(customers.save(customer));
        }
        Staff person = staff.findById(id).orElseThrow(() -> notFound("Không tìm thấy hồ sơ"));
        person.setFullName(request.fullName()); person.setPhone(request.phone()); person.setEmail(request.email());
        return profile(staff.save(person));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse customer(Long id) {
        Customer customer = customers.findById(id).orElseThrow(() -> notFound("Không tìm thấy hồ sơ khách hàng"));
        return profile(customer);
    }

    /**
     * Tra cứu khách hàng cho nhân sự khi tạo hợp đồng nháp / thêm người ở.
     * Trước đây FE phải nhập tay customerId; giờ chọn từ danh sách có tìm kiếm.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> listCustomers(String keyword, int page, int size) {
        Pageable pageable = pageable(page, size);
        String q = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return PageResponse.from(customers.search(q, pageable).map(this::profile));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> listStaff(Long buildingId, int page, int size) {
        HeaderUser user = CurrentUser.require();
        Pageable pageable = pageable(page, size);
        if (user.getRoles().contains("ADMIN")) {
            return PageResponse.from(staff.findStaff(buildingId, pageable)
                    .map(this::profile));
        }
        List<Long> managed = buildingIds(CurrentUser.id());
        if (buildingId != null) {
            if (!managed.contains(buildingId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "Bạn không có quyền quản lý tòa nhà này");
            }
            return PageResponse.from(staff.findStaff(buildingId, pageable)
                    .map(s -> profile(s, managed)));
        }
        if (managed.isEmpty()) {
            return PageResponse.from(org.springframework.data.domain.Page.empty(pageable));
        }
        return PageResponse.from(staff.findStaffInBuildings(managed, pageable)
                .map(s -> profile(s, managed)));
    }

    public UserProfileResponse updateStaff(Long id, UpdateStaffRequest request) {
        Staff person = staff.findById(id).orElseThrow(() -> notFound("Không tìm thấy hồ sơ nhân viên"));
        person.setFullName(request.fullName()); person.setPhone(request.phone()); person.setEmail(request.email());
        person.setPosition(request.position());
        return profile(staff.save(person));
    }

    @Transactional(readOnly = true)
    public List<Long> buildingIds(Long staffId) {
        return assignments.findByIdStaffId(staffId).stream().map(a -> a.getId().getBuildingId()).sorted().toList();
    }

    /** Danh ba nhan su theo toa de chat-service cho khach chon nguoi lien he. */
    @Transactional(readOnly = true)
    public List<StaffSummaryResponse> staffByBuilding(Long buildingId) {
        List<Long> ids = assignments.findByIdBuildingId(buildingId).stream()
                .map(a -> a.getId().getStaffId())
                .distinct()
                .toList();
        if (ids.isEmpty()) return List.of();
        return staff.findAllById(ids).stream()
                .sorted(java.util.Comparator.comparing(com.rental.user.entity.Staff::getId))
                .map(this::staffSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StaffSummaryResponse> staffSummaries(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return staff.findAllById(ids).stream()
                .sorted(java.util.Comparator.comparing(com.rental.user.entity.Staff::getId))
                .map(this::staffSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public void assertCanViewBuildings(Long staffId) {
        HeaderUser user = CurrentUser.require();
        if (user.getRoles().contains("ADMIN") || ("STAFF".equals(user.getUserType())
                && CurrentUser.id().equals(staffId))) return;
        throw new BusinessException(ErrorCode.FORBIDDEN, "Không đủ quyền xem phân công");
    }

    public void replaceBuildingIds(Long staffId, BuildingIdsRequest request) {
        if (!staff.existsById(staffId)) throw notFound("Không tìm thấy hồ sơ nhân viên");
        assignments.deleteAll(assignments.findByIdStaffId(staffId));
        request.buildingIds().stream().distinct().forEach(buildingId ->
                assignments.save(new StaffBuilding(new StaffBuildingId(staffId, buildingId))));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse staff(Long id) {
        return staff.findById(id).map(this::profile)
                .orElseThrow(() -> notFound("Không tìm thấy hồ sơ nhân viên"));
    }

    @Transactional(readOnly = true)
    public boolean existsStaff(Long id) { return staff.existsById(id); }

    private UserProfileResponse profile(Customer customer) {
        return new UserProfileResponse(customer.getId(), customer.getFullName(), customer.getPhone(),
                customer.getEmail(), null, null, null);
    }
    private StaffSummaryResponse staffSummary(Staff person) {
        return new StaffSummaryResponse(person.getId(), person.getFullName(), person.getPosition(),
                person.isAdmin());
    }
    private UserProfileResponse profile(Staff person) {
        return profile(person, null);
    }
    private UserProfileResponse profile(Staff person, List<Long> allowedBuildings) {
        List<Long> bIds = buildingIds(person.getId());
        if (allowedBuildings != null) {
            bIds = bIds.stream().filter(allowedBuildings::contains).sorted().toList();
        }
        return new UserProfileResponse(person.getId(), person.getFullName(), person.getPhone(), person.getEmail(),
                person.getPosition(), person.isAdmin(), bIds);
    }
    private static Pageable pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "page phải >= 0 và size trong khoảng 1..100");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
    }
    private static BusinessException notFound(String message) { return new BusinessException(ErrorCode.NOT_FOUND, message); }
}
