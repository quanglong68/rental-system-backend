package com.rental.contracttenant.service;

import com.rental.common.error.BusinessException;
import com.rental.common.security.HeaderUser;
import com.rental.contracttenant.client.StaffClient;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import feign.RetryableException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Spec 6.2 "Phạm vi tòa nhà": a staff member (not ADMIN) may only act on buildings they are
 * assigned to, checked through Feign call #5. ADMIN skips the check entirely.
 *
 * <p>Fail-closed by design: an unreadable assignment list or an unreachable User service rejects the
 * action with 503 (spec 5.2, Feign #5 row: "Từ chối thao tác, trả 503"). Treating "could not check"
 * as "allowed" would turn a dependency outage into an authorization bypass.
 */
@Component
public class BuildingScope {

    private final StaffClient staff;

    public BuildingScope(StaffClient staff) {
        this.staff = staff;
    }

    /** The verb-like name matches the codebase's existing {@code assertCanViewBuildings} idiom. */
    public void assertAssigned(Long buildingId) {
        HeaderUser user = currentUser();
        if (user.getRoles().contains("ADMIN")) {
            return;
        }
        List<Long> assigned = getAssignedBuildingIds(user);
        if (buildingId == null || !assigned.contains(buildingId)) {
            throw new AccessDeniedException("Staff chua duoc phan cong tai toa nha nay");
        }
    }

    public List<Long> getAssignedBuildingIds() {
        HeaderUser user = currentUser();
        if (user.getRoles().contains("ADMIN")) {
            return null; // indicates all buildings allowed
        }
        return getAssignedBuildingIds(user);
    }

    private List<Long> getAssignedBuildingIds(HeaderUser user) {
        List<Long> assigned;
        try {
            StaffClient.StaffBuildings response = staff.staffBuildings(Long.valueOf(user.getUserId()));
            assigned = response == null ? null : response.buildingIds();
        } catch (BusinessException | CallNotPermittedException | RetryableException unavailable) {
            throw new BusinessException("DEPENDENCY_UNAVAILABLE", "Khong kiem tra duoc phan cong toa nha",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        if (assigned == null) {
            throw new BusinessException("DEPENDENCY_UNAVAILABLE", "Khong kiem tra duoc phan cong toa nha",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        return assigned;
    }

    private static HeaderUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof HeaderUser user)) {
            throw new AccessDeniedException("Khong xac dinh duoc nguoi dung");
        }
        return user;
    }
}
