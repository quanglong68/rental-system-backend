package com.rental.billingutilityservice.support;

import com.rental.billingutilityservice.client.UserClient;
import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.security.HeaderUser;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tien ich phan quyen dung chung: lay principal, kiem tra ADMIN, kiem tra pham vi toa nha
 * cua staff qua Feign #5 (cache 5 phut). ADMIN bo qua buoc kiem tra toa nha (docs muc 6.2).
 */
@Component
@RequiredArgsConstructor
public class AuthSupport {

    private final UserClient userClient;

    public HeaderUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HeaderUser user) {
            return user;
        }
        throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Chưa đăng nhập");
    }

    public Long currentUserId() {
        return Long.valueOf(currentUser().getUserId());
    }

    public boolean isAdmin() {
        HeaderUser user = currentUser();
        return "ADMIN".equalsIgnoreCase(user.getUserType()) || user.getRoles().contains("ADMIN");
    }

    /** Scheduler chay nen khong co SecurityContext; dung de bo qua kiem tra pham vi. */
    public boolean hasAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof HeaderUser;
    }

    @Cacheable(value = "staffBuildings", key = "#staffId")
    public List<Long> staffBuildingIds(Long staffId) {
        UserClient.StaffBuildings res = userClient.getStaffBuildings(staffId);
        if (res == null || res.getBuildingIds() == null) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE,
                    "Không kiểm tra được phân công tòa nhà");
        }
        return res.getBuildingIds();
    }

    /** Staff (khong phai ADMIN) chi thao tac tren toa nha minh phu trach (docs muc 6.2). */
    public void requireBuildingScope(Long buildingId) {
        if (buildingId == null || isAdmin()) {
            return;
        }
        List<Long> buildings = staffBuildingIds(currentUserId());
        if (!buildings.contains(buildingId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Không phụ trách tòa nhà này");
        }
    }
}
