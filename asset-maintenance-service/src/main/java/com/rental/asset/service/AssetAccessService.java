package com.rental.asset.service;

import com.rental.asset.client.UserClient;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.security.HeaderUser;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AssetAccessService {

    private final UserClient userClient;

    public AssetAccessService(UserClient userClient) {
        this.userClient = userClient;
    }

    public HeaderUser currentUser() {
        return (HeaderUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    public Long currentUserId() {
        return Long.valueOf(currentUser().getUserId());
    }

    public boolean currentUserHasRole(String role) {
        return currentUser().getRoles().contains(role);
    }

    public boolean isAdmin() {
        return currentUserHasRole("ADMIN");
    }

    /** Danh sach toa nha staff hien tai duoc phan cong (ADMIN tra ve rong, dung requireBuildingAccess). */
    public List<Long> assignedBuildingIds() {
        return assignedBuildingIds(currentUserId());
    }

    /** Staff (khong phai ADMIN) chi thao tac tren toa nha minh phu trach (docs muc 6.2). */
    public void requireBuildingAccess(Long buildingId) {
        if (isAdmin()) {
            return;
        }
        if (buildingId == null || !assignedBuildingIds(currentUserId()).contains(buildingId)) {
            throw new AccessDeniedException("Staff chưa được phân công tại tòa nhà này");
        }
    }

    /**
     * Kiem tra nhan vien duoc phan cong toa nha (docs 9.6). Khac voi scope cua nguoi goi:
     * ADMIN cung khong duoc gan nham nhan vien ngoai toa, nen khong bypass.
     */
    public void requireStaffBelongsToBuilding(Long staffId, Long buildingId) {
        if (staffId == null || buildingId == null || !assignedBuildingIds(staffId).contains(buildingId)) {
            throw new BusinessException("STAFF_NOT_IN_BUILDING",
                    "Nhân viên được chọn không thuộc tòa nhà này", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private List<Long> assignedBuildingIds(Long staffId) {
        UserClient.StaffBuildingsResponse response;
        try {
            response = userClient.getStaffBuildings(staffId);
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "Không thể kiểm tra phân công nhân viên");
        }
        if (response == null || response.buildingIds() == null) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "Không thể kiểm tra phân công nhân viên");
        }
        return response.buildingIds();
    }
}
