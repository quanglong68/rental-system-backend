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
        throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Chua dang nhap");
    }

    public Long currentUserId() {
        return Long.valueOf(currentUser().getUserId());
    }

    public boolean isAdmin() {
        HeaderUser user = currentUser();
        return "ADMIN".equalsIgnoreCase(user.getUserType()) || user.getRoles().contains("ADMIN");
    }

    @Cacheable(value = "staffBuildings", key = "#staffId")
    public List<Long> staffBuildingIds(Long staffId) {
        UserClient.StaffBuildings res = userClient.getStaffBuildings(staffId);
        return res.getBuildingIds() == null ? List.of() : res.getBuildingIds();
    }

    /** Staff (khong phai ADMIN) chi thao tac tren toa nha minh phu trach (docs muc 6.2). */
    public void requireBuildingScope(Long buildingId) {
        if (buildingId == null || isAdmin()) {
            return;
        }
        List<Long> buildings = staffBuildingIds(currentUserId());
        if (!buildings.contains(buildingId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Khong phu trach toa nha nay");
        }
    }
}
