package org.scoutsdecanarias.tha_backend.features.access;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Los roles de rama no se guardan: se derivan del puesto vigente al emitir el token y caducan solos. */
class EffectiveRolesTest extends AbstractIntegrationTest {

    @Autowired BranchRepository branches;
    @Autowired BranchTeamMemberRepository team;
    @Autowired EffectiveRoles effectiveRoles;

    private static final LocalDate TODAY = LocalDate.now(ClockConfig.ZONE);

    private User person(Set<Role> roles) {
        return activeUser("puesto-" + UUID.randomUUID() + "@example.org", roles);
    }

    private void position(User user, TeamPosition position, LocalDate start, LocalDate end) {
        Branch castores = branches.findAll().stream().filter(b -> b.getCode().equals("CASTORES")).findFirst().orElseThrow();
        BranchTeamMember m = new BranchTeamMember();
        m.setUser(user);
        m.setBranch(castores);
        m.setPosition(position);
        m.setStartDate(start);
        m.setEndDate(end);
        team.save(m);
    }

    @SuppressWarnings("unchecked")
    private List<String> rolesInToken(String accessToken) {
        String payload = new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        return (List<String>) JsonPath.read(payload, "$.roles");
    }

    @Test
    void elPuestoVigenteAnadeElRolDeRamaAlTokenSinGuardarlo() throws Exception {
        for (TeamPosition position : TeamPosition.values()) {
            User user = person(Set.of(Role.MEMBER));
            position(user, position, TODAY.minusDays(5), TODAY.plusDays(5));

            String token = accessToken(user.getEmail());

            assertThat(rolesInToken(token)).containsExactlyInAnyOrder("MEMBER", position.role().name());
            assertThat(users.findById(user.getId()).orElseThrow().getRoles()).containsExactly(Role.MEMBER);
        }
    }

    @Test
    void elPuestoCaducadoOFuturoNoAnadeNada() throws Exception {
        User expired = person(Set.of(Role.MEMBER));
        position(expired, TeamPosition.COORDINACION, TODAY.minusDays(60), TODAY.minusDays(1));
        User future = person(Set.of(Role.MEMBER));
        position(future, TeamPosition.TESORERIA, TODAY.plusDays(1), TODAY.plusDays(60));

        assertThat(rolesInToken(accessToken(expired.getEmail()))).containsExactly("MEMBER");
        assertThat(rolesInToken(accessToken(future.getEmail()))).containsExactly("MEMBER");
    }

    @Test
    void unRolDeRamaGuardadoPorErrorNoConcedeNada() {
        User user = person(Set.of(Role.MEMBER, Role.BRANCH_TREASURER));
        assertThat(effectiveRoles.of(user)).containsExactly(Role.MEMBER);
    }

    @Test
    void conservaLosRolesGuardadosYSumaLosDerivadosDeVariosPuestos() {
        User user = person(Set.of(Role.MEMBER, Role.GROUP_COORDINATOR, Role.BOARD_MEMBER));
        position(user, TeamPosition.COORDINACION, TODAY, TODAY.plusDays(10));
        position(user, TeamPosition.SECRETARIA, TODAY.minusDays(3), TODAY.plusDays(3));

        assertThat(effectiveRoles.of(user)).containsExactlyInAnyOrder(
                Role.MEMBER, Role.GROUP_COORDINATOR, Role.BOARD_MEMBER, Role.BRANCH_COORDINATOR, Role.BRANCH_SECRETARY);
    }

    @Test
    void laRespuestaDelLoginYMeIncluyenLosRolesEfectivos() throws Exception {
        User user = person(Set.of(Role.MEMBER));
        position(user, TeamPosition.TESORERIA, TODAY.minusDays(1), TODAY.plusDays(1));

        MvcResult result = login(user.getEmail(), PASSWORD, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles", org.hamcrest.Matchers.hasItem("BRANCH_TREASURER"))).andReturn();
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.hasItem("BRANCH_TREASURER")));
    }
}
