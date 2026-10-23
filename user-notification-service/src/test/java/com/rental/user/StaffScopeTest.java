package com.rental.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rental.common.error.BusinessException;
import com.rental.common.error.ErrorCode;
import com.rental.common.security.HeaderUser;
import com.rental.user.dto.PageResponse;
import com.rental.user.dto.UserProfileResponse;
import com.rental.user.entity.Staff;
import com.rental.user.entity.StaffBuilding;
import com.rental.user.entity.StaffBuildingId;
import com.rental.user.repository.CustomerRepository;
import com.rental.user.repository.StaffBuildingRepository;
import com.rental.user.repository.StaffRepository;
import com.rental.user.service.UserProfileService;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class StaffScopeTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private StaffBuildingRepository staffBuildingRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String userId, String userType, List<String> roles) {
        HeaderUser principal = new HeaderUser(userId, userType, roles);
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private Staff mockStaff(Long id, String name, String email) {
        Staff s = new Staff();
        s.setId(id);
        s.setFullName(name);
        s.setEmail(email);
        s.setPhone("0900000000");
        s.setPosition("KY_THUAT");
        s.setAdmin(false);
        return s;
    }

    @Test
    @DisplayName("ADMIN: Can view all staff without building filter and see all assigned buildings")
    void admin_viewsAllStaff_seesAllBuildings() {
        authenticate("1", "ADMIN", List.of("ADMIN"));

        Staff tech = mockStaff(201L, "Nguyen Van A", "tech1@example.com");
        when(staffRepository.findStaff(eq(null), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tech)));
        when(staffBuildingRepository.findByIdStaffId(201L)).thenReturn(List.of(
                new StaffBuilding(new StaffBuildingId(201L, 10L)),
                new StaffBuilding(new StaffBuildingId(201L, 99L))));

        PageResponse<UserProfileResponse> result = userProfileService.listStaff(null, 0, 20);

        assertThat(result.content()).hasSize(1);
        UserProfileResponse profile = result.content().get(0);
        assertThat(profile.id()).isEqualTo(201L);
        // Admin sees all buildings including 10 and 99
        assertThat(profile.buildingIds()).containsExactly(10L, 99L);
        verify(staffRepository).findStaff(eq(null), any(Pageable.class));
    }

    @Test
    @DisplayName("QUAN_LY: Querying with in-scope building returns staff and filters out out-of-scope buildings to prevent data leak")
    void manager_inScopeBuilding_returnsStaffWithFilteredBuildings() {
        // Manager 101 manages building 10 and 20
        authenticate("101", "STAFF", List.of("QUAN_LY"));
        when(staffBuildingRepository.findByIdStaffId(101L)).thenReturn(List.of(
                new StaffBuilding(new StaffBuildingId(101L, 10L)),
                new StaffBuilding(new StaffBuildingId(101L, 20L))));

        // Tech 201 belongs to building 10 AND unmanaged building 99
        Staff tech = mockStaff(201L, "Nguyen Van A", "tech1@example.com");
        when(staffRepository.findStaff(eq(10L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tech)));
        when(staffBuildingRepository.findByIdStaffId(201L)).thenReturn(List.of(
                new StaffBuilding(new StaffBuildingId(201L, 10L)),
                new StaffBuilding(new StaffBuildingId(201L, 99L))));

        PageResponse<UserProfileResponse> result = userProfileService.listStaff(10L, 0, 20);

        assertThat(result.content()).hasSize(1);
        UserProfileResponse profile = result.content().get(0);
        assertThat(profile.id()).isEqualTo(201L);
        // Data leak prevention: Building 99 is filtered out, manager only sees building 10
        assertThat(profile.buildingIds()).containsExactly(10L);
        assertThat(profile.buildingIds()).doesNotContain(99L);
    }

    @Test
    @DisplayName("QUAN_LY: Querying with out-of-scope building throws FORBIDDEN")
    void manager_outOfScopeBuilding_throwsForbidden() {
        // Manager 101 only manages building 10
        authenticate("101", "STAFF", List.of("QUAN_LY"));
        when(staffBuildingRepository.findByIdStaffId(101L)).thenReturn(List.of(
                new StaffBuilding(new StaffBuildingId(101L, 10L))));

        // Attempting to query building 30 (not in manager's buildings)
        assertThatThrownBy(() -> userProfileService.listStaff(30L, 0, 20))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("FORBIDDEN");
                    assertThat(be.getStatus()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
                });

        verify(staffRepository, never()).findStaff(any(), any());
    }

    @Test
    @DisplayName("QUAN_LY: Querying without buildingId scopes to caller's managed buildings without querying all staff")
    void manager_omittedBuildingId_scopesToManagedBuildings() {
        // Manager 101 manages building 10 and 20
        authenticate("101", "STAFF", List.of("QUAN_LY"));
        when(staffBuildingRepository.findByIdStaffId(101L)).thenReturn(List.of(
                new StaffBuilding(new StaffBuildingId(101L, 10L)),
                new StaffBuilding(new StaffBuildingId(101L, 20L))));

        Staff tech = mockStaff(201L, "Nguyen Van A", "tech1@example.com");
        when(staffRepository.findStaffInBuildings(eq(List.of(10L, 20L)), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tech)));
        when(staffBuildingRepository.findByIdStaffId(201L)).thenReturn(List.of(
                new StaffBuilding(new StaffBuildingId(201L, 10L)),
                new StaffBuilding(new StaffBuildingId(201L, 99L))));

        PageResponse<UserProfileResponse> result = userProfileService.listStaff(null, 0, 20);

        assertThat(result.content()).hasSize(1);
        UserProfileResponse profile = result.content().get(0);
        assertThat(profile.buildingIds()).containsExactly(10L);
        // Verify findStaff(null, ...) was NOT called (which would fetch all staff)
        verify(staffRepository, never()).findStaff(eq(null), any());
        verify(staffRepository).findStaffInBuildings(eq(List.of(10L, 20L)), any(Pageable.class));
    }

    @Test
    @DisplayName("QUAN_LY: Manager with no assigned buildings returns empty page immediately")
    void manager_noBuildings_returnsEmptyPageWithoutQueryingStaff() {
        authenticate("102", "STAFF", List.of("QUAN_LY"));
        when(staffBuildingRepository.findByIdStaffId(102L)).thenReturn(List.of());

        PageResponse<UserProfileResponse> result = userProfileService.listStaff(null, 0, 20);

        assertThat(result.content()).isEmpty();
        verify(staffRepository, never()).findStaff(any(), any());
        verify(staffRepository, never()).findStaffInBuildings(any(), any());
    }
}
