package fptu.semester8.mss301.propertiesservice.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PropertyScopeTest {
    private final StaffBuildingAssignments assignments = mock(StaffBuildingAssignments.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final PropertyScope scope = new PropertyScope(assignments, jdbc);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void managerCanUseAssignedBuildingButNotAnother() {
        signIn("51", "QUAN_LY");
        when(assignments.forStaff(51)).thenReturn(Set.of(10L));
        assertDoesNotThrow(() -> scope.requireBuilding(10));
        assertThrows(AccessDeniedException.class, () -> scope.requireBuilding(11));
    }

    @Test
    void roomScopeComesFromDatabaseNotRequestBuildingId() {
        signIn("51", "QUAN_LY");
        when(assignments.forStaff(51)).thenReturn(Set.of(10L));
        when(jdbc.query(eq("SELECT building_id FROM room WHERE room_id = ?"),
                any(org.springframework.jdbc.core.RowMapper.class), eq(99L))).thenReturn(List.of(11L));
        assertThrows(AccessDeniedException.class, () -> scope.requireRoom(99));
    }

    @Test
    void managerCanUseRoomInAssignedBuilding() {
        signIn("51", "QUAN_LY");
        when(assignments.forStaff(51)).thenReturn(Set.of(10L));
        when(jdbc.query(eq("SELECT building_id FROM room WHERE room_id = ?"),
                any(org.springframework.jdbc.core.RowMapper.class), eq(99L))).thenReturn(List.of(10L));
        assertDoesNotThrow(() -> scope.requireRoom(99));
    }

    @Test
    void assignmentFailureAndEmptyListFailClosed() {
        signIn("51", "SALE");
        when(assignments.forStaff(51)).thenThrow(new IllegalStateException("downstream unavailable"));
        assertTrue(scope.assignedBuildings().isEmpty());
        assertThrows(AccessDeniedException.class, () -> scope.requireBuilding(10));
    }

    @Test
    void adminDoesNotNeedAssignmentLookup() {
        signIn("1", "ADMIN");
        assertDoesNotThrow(() -> scope.requireBuilding(10));
        assertDoesNotThrow(() -> scope.requireRoom(99));
        verifyNoInteractions(assignments, jdbc);
    }

    private static void signIn(String id, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                id, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
}
