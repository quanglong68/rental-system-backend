package com.rental.asset.service;

import com.rental.asset.client.UserClient;
import com.rental.common.error.BusinessException;
import com.rental.common.security.HeaderUser;
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

    public void requireBuildingAccess(Long buildingId) {
        if (!currentUserHasRole("ADMIN")) {
            requireStaffAssignment(currentUserId(), buildingId);
        }
    }

    public void requireStaffAssignment(Long staffId, Long buildingId) {
        if (currentUserHasRole("ADMIN")) {
            return;
        }

        try {
            boolean isAssigned = userClient.getStaffBuildings(staffId).buildingIds().contains(buildingId);
            if (!isAssigned) {
                throw new AccessDeniedException("Staff chưa được phân công tại tòa nhà này");
            }
        } catch (AccessDeniedException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(
                    "DEPENDENCY_UNAVAILABLE",
                    "Không thể kiểm tra phân công nhân viên",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
