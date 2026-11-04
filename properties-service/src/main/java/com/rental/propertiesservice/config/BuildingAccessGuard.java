package com.rental.propertiesservice.config;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.security.HeaderUser;
import com.rental.propertiesservice.client.StaffClient;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Spec 6.2 "Phạm vi tòa nhà": a non-ADMIN staff member may only read or write buildings assigned to
 * them through {@code staff_building}. ADMIN bypasses the lookup. The list is never cached: a stale
 * assignment must not keep granting access after ADMIN revokes it.
 */
@Component
@RequiredArgsConstructor
public class BuildingAccessGuard {

    private static final Set<String> STAFF_ROLES = Set.of("QUAN_LY", "SALE", "KY_THUAT");

    private final StaffClient staff;

    public boolean isAdmin() {
        return authorities().contains("ROLE_ADMIN");
    }

    public boolean isStaff() {
        return authorities().stream().anyMatch(STAFF_ROLES::contains);
    }

    public Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof HeaderUser user)) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Không xác định được người dùng");
        }
        try {
            return Long.valueOf(user.getUserId());
        } catch (NumberFormatException invalid) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "Không xác định được người dùng");
        }
    }

    public void assertAssigned(Long buildingId) {
        if (isAdmin()) {
            return;
        }
        if (buildingId == null || !assignedBuildingIds().contains(buildingId)) {
            throw new BusinessException("BUILDING_ACCESS_DENIED",
                    "Staff chưa được phân công tại tòa nhà này", HttpStatus.FORBIDDEN);
        }
    }

    /** {@code null} means "every building" (ADMIN); otherwise the caller is limited to this list. */
    public List<Long> allowedBuildingIdsOrNull() {
        return isAdmin() ? null : assignedBuildingIds();
    }

    public List<Long> assignedBuildingIds() {
        StaffClient.StaffBuildings buildings = staff.staffBuildings(currentUserId());
        return buildings == null || buildings.buildingIds() == null ? List.of() : buildings.buildingIds();
    }

    private Set<String> authorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
    }
}
