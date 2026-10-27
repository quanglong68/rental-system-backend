package com.rental.listingservice.features.listing.application;

import com.rental.common.security.HeaderUser;
import com.rental.listingservice.integration.staff.StaffAssignmentClient;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class ListingAuthorizationService {

    private final StaffAssignmentClient staffAssignmentClient;

    public ListingAuthorizationService(StaffAssignmentClient staffAssignmentClient) {
        this.staffAssignmentClient = staffAssignmentClient;
    }

    public void requireBuildingAccess(Long buildingId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof HeaderUser user)) {
            throw new AccessDeniedException("Authenticated staff identity is required");
        }

        if (user.getRoles().contains("ADMIN")) {
            return;
        }
        if (!"STAFF".equals(user.getUserType())) {
            throw new AccessDeniedException("Staff access is required");
        }

        long staffId;
        try {
            staffId = Long.parseLong(user.getUserId());
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException("Invalid staff identity");
        }
        var assignment = staffAssignmentClient.getStaffBuildings(staffId);
        if (assignment == null || assignment.buildingIds() == null
                || assignment.buildingIds().stream().noneMatch(id -> Objects.equals(id, buildingId))) {
            throw new AccessDeniedException("No access to this building");
        }
    }
}
