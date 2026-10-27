package fptu.semester8.mss301.propertiesservice.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/** Resolves authorization against stored resource ownership, not client-supplied IDs. */
@Component
public class PropertyScope {
    private final StaffBuildingAssignments assignments;
    private final JdbcTemplate jdbc;

    public PropertyScope(StaffBuildingAssignments assignments, JdbcTemplate jdbc) {
        this.assignments = assignments;
        this.jdbc = jdbc;
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public Set<Long> assignedBuildings() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || isAdmin() || !isStaff()) return Set.of();
        try { return assignments.forStaff(Long.parseLong(auth.getName())); }
        catch (RuntimeException invalid) { return Set.of(); }
    }

    public void requireBuilding(long buildingId) {
        if (!isAdmin() && !assignedBuildings().contains(buildingId)) {
            throw new AccessDeniedException("Building access denied");
        }
    }

    public void requireRoom(long roomId) {
        if (isAdmin()) return;
        List<Long> buildings = jdbc.query("SELECT building_id FROM room WHERE room_id = ?",
                (rs, row) -> rs.getLong(1), roomId);
        if (buildings.isEmpty()) throw new AccessDeniedException("Room access denied");
        requireBuilding(buildings.getFirst());
    }

    private boolean isStaff() {
        return hasRole("QUAN_LY") || hasRole("SALE") || hasRole("KY_THUAT");
    }

    private boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
}
