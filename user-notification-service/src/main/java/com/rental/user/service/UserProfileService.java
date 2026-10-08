package com.rental.user.service;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.security.HeaderUser;
import com.rental.user.dto.BuildingIdsRequest;
import com.rental.user.dto.PageResponse;
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
            Customer customer = customers.findById(id).orElseThrow(() -> notFound("Khong tim thay ho so"));
            customer.setFullName(request.fullName()); customer.setPhone(request.phone()); customer.setEmail(request.email());
            return profile(customers.save(customer));
        }
        Staff person = staff.findById(id).orElseThrow(() -> notFound("Khong tim thay ho so"));
        person.setFullName(request.fullName()); person.setPhone(request.phone()); person.setEmail(request.email());
        return profile(staff.save(person));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse customer(Long id) {
        Customer customer = customers.findById(id).orElseThrow(() -> notFound("Khong tim thay ho so khach hang"));
        return profile(customer);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserProfileResponse> listStaff(Long buildingId, int page, int size) {
        return PageResponse.from(staff.findStaff(buildingId, pageable(page, size))
                .map(this::profile));
    }

    public UserProfileResponse updateStaff(Long id, UpdateStaffRequest request) {
        Staff person = staff.findById(id).orElseThrow(() -> notFound("Khong tim thay ho so nhan vien"));
        person.setFullName(request.fullName()); person.setPhone(request.phone()); person.setEmail(request.email());
        person.setPosition(request.position());
        return profile(staff.save(person));
    }

    @Transactional(readOnly = true)
    public List<Long> buildingIds(Long staffId) {
        return assignments.findByIdStaffId(staffId).stream().map(a -> a.getId().getBuildingId()).sorted().toList();
    }

    @Transactional(readOnly = true)
    public void assertCanViewBuildings(Long staffId) {
        HeaderUser user = CurrentUser.require();
        if (user.getRoles().contains("ADMIN") || ("STAFF".equals(user.getUserType())
                && CurrentUser.id().equals(staffId))) return;
        throw new BusinessException(ErrorCode.FORBIDDEN, "Khong du quyen xem phan cong");
    }

    public void replaceBuildingIds(Long staffId, BuildingIdsRequest request) {
        if (!staff.existsById(staffId)) throw notFound("Khong tim thay ho so nhan vien");
        assignments.deleteAll(assignments.findByIdStaffId(staffId));
        request.buildingIds().stream().distinct().forEach(buildingId ->
                assignments.save(new StaffBuilding(new StaffBuildingId(staffId, buildingId))));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse staff(Long id) {
        return staff.findById(id).map(this::profile)
                .orElseThrow(() -> notFound("Khong tim thay ho so nhan vien"));
    }

    @Transactional(readOnly = true)
    public boolean existsStaff(Long id) { return staff.existsById(id); }

    private UserProfileResponse profile(Customer customer) {
        return new UserProfileResponse(customer.getId(), customer.getFullName(), customer.getPhone(),
                customer.getEmail(), null, null, null);
    }
    private UserProfileResponse profile(Staff person) {
        return new UserProfileResponse(person.getId(), person.getFullName(), person.getPhone(), person.getEmail(),
                person.getPosition(), person.isAdmin(), buildingIds(person.getId()));
    }
    private static Pageable pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "page phai >= 0 va size trong khoang 1..100");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
    }
    private static BusinessException notFound(String message) { return new BusinessException(ErrorCode.NOT_FOUND, message); }
}
