package com.rental.listingbooking.service;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.security.HeaderUser;
import com.rental.listingbooking.client.UserClient;
import com.rental.listingbooking.entity.RoomListing;
import com.rental.listingbooking.repository.RoomListingRepository;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import feign.RetryableException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Spec 6.2 "Phạm vi tòa nhà": staff (không phải ADMIN) chỉ thao tác trên tòa nhà được phân công.
 *
 * <p>Fail-closed: không đọc được danh sách phân công thì từ chối thao tác với 503 thay vì coi như
 * được phép, tránh biến sự cố phụ thuộc thành lỗ hổng phân quyền.
 */
@Component
public class BuildingScope {

    private final UserClient userClient;
    private final RoomListingRepository listingRepository;

    public BuildingScope(UserClient userClient, RoomListingRepository listingRepository) {
        this.userClient = userClient;
        this.listingRepository = listingRepository;
    }

    /** Staff hiện tại phải được phân công tòa nhà; ADMIN bỏ qua. */
    public void assertAssigned(Long buildingId) {
        HeaderUser user = currentUser();
        if (user.getRoles().contains("ADMIN")) {
            return;
        }
        List<Long> assigned = fetchAssignedBuildingIds(Long.valueOf(user.getUserId()));
        if (buildingId == null || !assigned.contains(buildingId)) {
            throw new AccessDeniedException("Nhân viên chưa được phân công tòa nhà này");
        }
    }

    /**
     * Nhân viên được chọn dẫn khách (có thể khác người tạo lịch) phải thuộc tòa nhà của phòng.
     * Sai phân công trả 422 STAFF_NOT_IN_BUILDING đúng đặc tả 9.3.
     */
    public void assertStaffAssigned(Long staffId, Long buildingId) {
        if (staffId == null || buildingId == null) {
            throw new BusinessException("STAFF_NOT_IN_BUILDING",
                    "Nhân viên được chọn không thuộc tòa nhà của phòng", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        List<Long> assigned = fetchAssignedBuildingIds(staffId);
        if (!assigned.contains(buildingId)) {
            throw new BusinessException("STAFF_NOT_IN_BUILDING",
                    "Nhân viên được chọn không thuộc tòa nhà của phòng", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    /** null = ADMIN, không giới hạn tòa nhà. */
    public List<Long> allowedBuildingIdsOrNull() {
        HeaderUser user = currentUser();
        if (user.getRoles().contains("ADMIN")) {
            return null;
        }
        return fetchAssignedBuildingIds(Long.valueOf(user.getUserId()));
    }

    private List<Long> fetchAssignedBuildingIds(Long staffId) {
        List<Long> assigned;
        try {
            UserClient.StaffBuildingsResponse response = userClient.getStaffBuildings(staffId);
            assigned = response == null ? null : response.buildingIds();
        } catch (BusinessException | CallNotPermittedException | RetryableException unavailable) {
            throw dependencyUnavailable();
        }
        if (assigned == null) {
            throw dependencyUnavailable();
        }
        return assigned;
    }

    private static BusinessException dependencyUnavailable() {
        return new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                "Không kiểm tra được phân công tòa nhà");
    }

    private static HeaderUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof HeaderUser user)) {
            throw new AccessDeniedException("Không xác định được người dùng");
        }
        return user;
    }

    /** Find active listing by ID for customer self-booking validation. */
    public RoomListing findListing(Long listingId) {
        return listingRepository.findById(listingId).orElseThrow(
            () -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy tin đăng")
        );
    }
}
