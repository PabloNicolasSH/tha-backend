package org.scoutsdecanarias.tha_backend.features.access;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.scoutsdecanarias.tha_backend.AbstractIntegrationTest;
import org.scoutsdecanarias.tha_backend.core.time.ClockConfig;
import org.scoutsdecanarias.tha_backend.features.branch.Branch;
import org.scoutsdecanarias.tha_backend.features.branch.BranchRepository;
import org.scoutsdecanarias.tha_backend.features.branch.BranchTeamMember;
import org.scoutsdecanarias.tha_backend.features.branch.BranchTeamMemberRepository;
import org.scoutsdecanarias.tha_backend.features.branch.TeamPosition;
import org.scoutsdecanarias.tha_backend.features.user.Role;
import org.scoutsdecanarias.tha_backend.features.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@code ScopeAccess} se puede usar en las anotaciones con {@code @scope}, con la persona autenticada. */
@Import(ScopeAnnotationTest.ProbeConfig.class)
class ScopeAnnotationTest extends AbstractIntegrationTest {

    static class Probe {
        @PreAuthorize("@scope.canManageBranch(#branchId)")
        public String branch(Long branchId) {
            return "ok";
        }

        @PreAuthorize("@scope.canManageGroupMembers(#groupId)")
        public String groupMembers(Long groupId) {
            return "ok";
        }

        @PreAuthorize("@scope.isFederationStaff()")
        public String federation() {
            return "ok";
        }
    }

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        Probe scopeProbe() {
            return new Probe();
        }
    }

    @Autowired Probe probe;
    @Autowired BranchRepository branches;
    @Autowired BranchTeamMemberRepository team;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void actAs(User user, String... authorities) {
        String[] withPrefix = java.util.Arrays.stream(authorities).map(a -> "ROLE_" + a).toArray(String[]::new);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(String.valueOf(user.getId()), "n/a", withPrefix));
    }

    private Branch branch(String code) {
        return branches.findAll().stream().filter(b -> b.getCode().equals(code)).findFirst().orElseThrow();
    }

    @Test
    void laAnotacionPermiteSoloLaRamaDelPuestoVigente() {
        User user = activeUser("anot-" + UUID.randomUUID() + "@example.org", Set.of(Role.MEMBER));
        BranchTeamMember m = new BranchTeamMember();
        m.setUser(user);
        m.setBranch(branch("CASTORES"));
        m.setPosition(TeamPosition.COORDINACION);
        LocalDate today = LocalDate.now(ClockConfig.ZONE);
        m.setStartDate(today.minusDays(1));
        m.setEndDate(today.plusDays(1));
        team.save(m);
        actAs(user, "MEMBER", "BRANCH_COORDINATOR");

        assertThat(probe.branch(branch("CASTORES").getId())).isEqualTo("ok");
        assertThatThrownBy(() -> probe.branch(branch("LOBATOS").getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(probe::federation).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void sinSesionOSinPermisoLaAnotacionDeniega() {
        assertThatThrownBy(() -> probe.branch(1L)).isInstanceOf(AccessDeniedException.class);

        User member = activeUser("anot-" + UUID.randomUUID() + "@example.org", Set.of(Role.MEMBER));
        actAs(member, "MEMBER");
        assertThatThrownBy(() -> probe.groupMembers(1L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void laFederacionPasaLasAnotaciones() {
        User admin = activeUser("anot-" + UUID.randomUUID() + "@example.org", Set.of(Role.ADMIN));
        actAs(admin, "ADMIN");

        assertThat(probe.federation()).isEqualTo("ok");
        assertThat(probe.branch(branch("ROVERS").getId())).isEqualTo("ok");
        assertThat(probe.groupMembers(1L)).isEqualTo("ok");
    }
}
