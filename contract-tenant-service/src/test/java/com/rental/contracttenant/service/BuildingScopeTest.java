package com.rental.contracttenant.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rental.common.error.BusinessException;
import com.rental.common.error.DependencyUnavailableException;
import com.rental.common.security.HeaderUser;
import com.rental.contracttenant.client.StaffClient;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Spec 6.2 building scope and its fail-closed rule (spec 5.2, Feign #5: an unreadable assignment
 * list rejects the action with 503). A plain fake, not a mock: the point is which paths reject.
 */
class BuildingScopeTest {

    /** Assignment list per staff id; a null value stands for "the call returned no body". */
    private static final class FakeStaff implements StaffClient {
        private final List<Long> assigned;
        private final RuntimeException failure;

        private FakeStaff(List<Long> assigned, RuntimeException failure) {
            this.assigned = assigned;
            this.failure = failure;
        }

        @Override
        public StaffBuildings staffBuildings(Long staffId) {
            if (failure != null) {
                throw failure;
            }
            return assigned == null ? null : new StaffBuildings(staffId, assigned);
        }
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assignedBuildingIsAllowed() {
        signInAs("501", List.of("QUAN_LY"));

        assertThatCode(() -> new BuildingScope(new FakeStaff(List.of(3L, 4L), null)).assertAssigned(4L))
                .doesNotThrowAnyException();
    }

    @Test
    void unassignedBuildingIsForbidden() {
        signInAs("501", List.of("QUAN_LY"));

        assertThatThrownBy(() -> new BuildingScope(new FakeStaff(List.of(3L), null)).assertAssigned(4L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminSkipsTheDependencyEntirely() {
        signInAs("900", List.of("ADMIN"));

        // A fake that always fails proves the call is not made: ADMIN must not depend on the User
        // service to act on any building.
        assertThatCode(() -> new BuildingScope(new FakeStaff(null, new IllegalStateException("called")))
                .assertAssigned(4L))
                .doesNotThrowAnyException();
    }

    @Test
    void unreachableUserServiceClosesWith503() {
        signInAs("501", List.of("QUAN_LY"));

        assertThatThrownBy(() -> new BuildingScope(new FakeStaff(null,
                new DependencyUnavailableException("user down"))).assertAssigned(4L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions
                        .assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void assignmentListWithoutABodyClosesWith503RatherThanAllowing() {
        signInAs("501", List.of("QUAN_LY"));

        assertThatThrownBy(() -> new BuildingScope(new FakeStaff(null, null)).assertAssigned(4L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions
                        .assertThat(((BusinessException) ex).getCode())
                        .isEqualTo("DEPENDENCY_UNAVAILABLE"));
    }

    private static void signInAs(String userId, List<String> roles) {
        HeaderUser user = new HeaderUser(userId, "STAFF", roles);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null,
                roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList()));
    }
}
