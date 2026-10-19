package org.scoutsdecanarias.tha_backend.features.access;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.scoutsdecanarias.tha_backend.AbstractIntegrationTest;
import org.scoutsdecanarias.tha_backend.core.time.ClockConfig;
import org.scoutsdecanarias.tha_backend.features.branch.Branch;
import org.scoutsdecanarias.tha_backend.features.branch.BranchRepository;
import org.scoutsdecanarias.tha_backend.features.branch.BranchTeamMember;
import org.scoutsdecanarias.tha_backend.features.branch.BranchTeamMemberRepository;
import org.scoutsdecanarias.tha_backend.features.branch.TeamPosition;
import org.scoutsdecanarias.tha_backend.features.scout_group.ScoutGroup;
import org.scoutsdecanarias.tha_backend.features.scout_group.ScoutGroupRepository;
import org.scoutsdecanarias.tha_backend.features.user.Role;
import org.scoutsdecanarias.tha_backend.features.user.User;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Matriz de permisos por ámbito: cada rol contra «su ámbito», «otro ámbito» y «puesto caducado», con el reloj
 * fijado a mano. Hoy, para estos tests, es el 10 de diciembre de 2026 en Canarias.
 */
class ScopeAccessTest extends AbstractIntegrationTest {

    static final LocalDate TODAY = LocalDate.of(2026, 12, 10);
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-12-10T12:00:00Z"), ClockConfig.ZONE);

    @Autowired ScoutGroupRepository groups;
    @Autowired BranchRepository branches;
    @Autowired BranchTeamMemberRepository team;

    ScopeAccess scope;
    ScoutGroup groupA;
    ScoutGroup groupB;
    Branch castores;
    Branch lobatos;

    @BeforeEach
    void setUp() {
        scope = new ScopeAccess(team, users, CLOCK);
        groupA = group("Grupo A");
        groupB = group("Grupo B");
        castores = branch("CASTORES");
        lobatos = branch("LOBATOS");
    }

    private ScoutGroup group(String name) {
        ScoutGroup g = new ScoutGroup();
        g.setName(name + " " + UUID.randomUUID());
        return groups.save(g);
    }

    private Branch branch(String code) {
        return branches.findAll().stream().filter(b -> b.getCode().equals(code)).findFirst().orElseThrow();
    }

    private User person(Set<Role> roles, ScoutGroup group, Branch personalBranch) {
        User user = activeUser("ambito-" + UUID.randomUUID() + "@example.org", roles);
        user.setScoutGroup(group);
        user.setBranch(personalBranch);
        return users.save(user);
    }

    private void position(User user, Branch branch, TeamPosition position, LocalDate start, LocalDate end) {
        BranchTeamMember m = new BranchTeamMember();
        m.setUser(user);
        m.setBranch(branch);
        m.setPosition(position);
        m.setStartDate(start);
        m.setEndDate(end);
        team.save(m);
    }

    private boolean branchAccess(User u, Set<Role> roles, Branch b) {
        return scope.canManageBranch(u.getId(), roles, b.getId());
    }

    // ---------- Federación ----------

    @Test
    void losRolesDeFederacionActuanSobreTodosLosGruposYRamas() {
        for (Role role : EnumSet.of(Role.ADMIN, Role.SECRETARY, Role.TREASURER, Role.BOARD_MEMBER, Role.LOGISTICS)) {
            Set<Role> roles = Set.of(role);
            User u = person(roles, null, null);

            assertThat(scope.isFederationStaff(roles)).as("%s es federación", role).isTrue();
            assertThat(branchAccess(u, roles, castores)).as("%s rama propia", role).isTrue();
            assertThat(branchAccess(u, roles, lobatos)).as("%s otra rama", role).isTrue();
            assertThat(scope.canManageGroup(u.getId(), roles, groupA.getId())).as("%s grupo A", role).isTrue();
            assertThat(scope.canManageGroup(u.getId(), roles, groupB.getId())).as("%s grupo B", role).isTrue();
        }
    }

    @Test
    void soloAdminYSecretariaAdministranLasPersonasDeUnGrupo() {
        for (Role role : EnumSet.of(Role.ADMIN, Role.SECRETARY)) {
            User u = person(Set.of(role), null, null);
            assertThat(scope.canManageGroupMembers(u.getId(), Set.of(role), groupA.getId())).as("%s", role).isTrue();
            assertThat(scope.canManageGroupMembers(u.getId(), Set.of(role), groupB.getId())).as("%s", role).isTrue();
        }
        for (Role role : EnumSet.of(Role.TREASURER, Role.BOARD_MEMBER, Role.LOGISTICS, Role.MODERATOR, Role.MEMBER)) {
            User u = person(Set.of(role), groupA, null);
            assertThat(scope.canManageGroupMembers(u.getId(), Set.of(role), groupA.getId())).as("%s", role).isFalse();
        }
    }

    // ---------- Sin rol de ámbito ----------

    @Test
    void miembrosYModeracionNoGestionanNingunAmbito() {
        for (Role role : EnumSet.of(Role.MEMBER, Role.MODERATOR)) {
            Set<Role> roles = Set.of(role);
            User u = person(roles, groupA, castores); // tener grupo y rama personales no concede nada
            assertThat(scope.isFederationStaff(roles)).isFalse();
            assertThat(scope.canManageGroup(u.getId(), roles, groupA.getId())).as("%s grupo", role).isFalse();
            assertThat(scope.canManageGroupMembers(u.getId(), roles, groupA.getId())).as("%s personas", role).isFalse();
            assertThat(branchAccess(u, roles, castores)).as("%s rama", role).isFalse();
        }
    }

    // ---------- Coordinación de grupo ----------

    @Test
    void laCoordinacionDeGrupoSoloActuaSobreSuGrupo() {
        Set<Role> roles = Set.of(Role.GROUP_COORDINATOR, Role.MEMBER);
        User u = person(roles, groupA, null);

        assertThat(scope.canManageGroup(u.getId(), roles, groupA.getId())).as("su grupo").isTrue();
        assertThat(scope.canManageGroupMembers(u.getId(), roles, groupA.getId())).as("sus personas").isTrue();
        assertThat(scope.canManageGroup(u.getId(), roles, groupB.getId())).as("otro grupo").isFalse();
        assertThat(scope.canManageGroupMembers(u.getId(), roles, groupB.getId())).as("personas de otro grupo").isFalse();
        assertThat(branchAccess(u, roles, castores)).as("no es de ninguna rama").isFalse();
        assertThat(scope.isFederationStaff(roles)).isFalse();
    }

    @Test
    void laCoordinacionDeGrupoSinGrupoNoGestionaNinguno() {
        Set<Role> roles = Set.of(Role.GROUP_COORDINATOR);
        User u = person(roles, null, null);
        assertThat(scope.canManageGroup(u.getId(), roles, groupA.getId())).isFalse();
        assertThat(scope.canManageGroupMembers(u.getId(), roles, groupA.getId())).isFalse();
    }

    @Test
    void sinElRolGroupCoordinatorTenerGrupoNoBasta() {
        Set<Role> roles = Set.of(Role.MEMBER);
        User u = person(roles, groupA, null);
        assertThat(scope.canManageGroup(u.getId(), roles, groupA.getId())).isFalse();
    }

    // ---------- Equipo de una rama ----------

    @Test
    void cualquierPuestoVigenteDaPermisoSobreSuRamaYNoSobreOtra() {
        for (TeamPosition position : TeamPosition.values()) {
            Set<Role> roles = Set.of(Role.MEMBER, position.role());
            User u = person(roles, null, null);
            position(u, castores, position, TODAY.minusDays(30), TODAY.plusDays(30));

            assertThat(branchAccess(u, roles, castores)).as("%s su rama", position).isTrue();
            assertThat(branchAccess(u, roles, lobatos)).as("%s otra rama", position).isFalse();
            assertThat(scope.canManageGroup(u.getId(), roles, groupA.getId())).as("%s no gestiona grupos", position).isFalse();
            assertThat(scope.isFederationStaff(roles)).as("%s no es federación", position).isFalse();
        }
    }

    @Test
    void elPuestoCaducadoOFuturoNoDaPermisoAunqueElTokenLlevaraElRol() {
        // El rol derivado del token no cuenta: lo que manda es el puesto con la fecha de hoy
        Set<Role> staleToken = Set.of(Role.MEMBER, Role.BRANCH_COORDINATOR);
        User expired = person(staleToken, null, null);
        position(expired, castores, TeamPosition.COORDINACION, TODAY.minusDays(60), TODAY.minusDays(1));
        User future = person(staleToken, null, null);
        position(future, castores, TeamPosition.COORDINACION, TODAY.plusDays(1), TODAY.plusDays(60));
        User never = person(staleToken, null, null);

        assertThat(branchAccess(expired, staleToken, castores)).as("caducó ayer").isFalse();
        assertThat(branchAccess(future, staleToken, castores)).as("empieza mañana").isFalse();
        assertThat(branchAccess(never, staleToken, castores)).as("nunca tuvo puesto").isFalse();
    }

    @Test
    void elPrimerYElUltimoDiaDelPuestoCuentan() {
        Set<Role> roles = Set.of(Role.MEMBER);
        User lastDay = person(roles, null, null);
        position(lastDay, castores, TeamPosition.TESORERIA, TODAY.minusDays(90), TODAY);
        User firstDay = person(roles, null, null);
        position(firstDay, castores, TeamPosition.SECRETARIA, TODAY, TODAY.plusDays(90));

        assertThat(branchAccess(lastDay, roles, castores)).isTrue();
        assertThat(branchAccess(firstDay, roles, castores)).isTrue();
    }

    @Test
    void variasPersonasPuedenTenerElMismoPuestoYUnaPersonaVariosPuestos() {
        Set<Role> roles = Set.of(Role.MEMBER);
        User ana = person(roles, null, null);
        User luis = person(roles, null, null);
        position(ana, castores, TeamPosition.COORDINACION, TODAY.minusDays(1), TODAY.plusDays(100));
        position(luis, castores, TeamPosition.COORDINACION, TODAY.minusDays(1), TODAY.plusDays(100));
        position(ana, lobatos, TeamPosition.SECRETARIA, TODAY.minusDays(1), TODAY.plusDays(100));

        assertThat(branchAccess(ana, roles, castores)).isTrue();
        assertThat(branchAccess(luis, roles, castores)).isTrue();
        assertThat(branchAccess(ana, roles, lobatos)).isTrue();
        assertThat(branchAccess(luis, roles, lobatos)).isFalse();
    }

    @Test
    void laRamaPersonalNoConcedePermisos() {
        Set<Role> roles = Set.of(Role.MEMBER);
        User u = person(roles, null, castores);
        assertThat(branchAccess(u, roles, castores)).isFalse();
    }

    @Test
    void sinIdentificadorNoSeConcedeNada() {
        Set<Role> roles = Set.of(Role.ADMIN);
        User u = person(roles, null, null);
        assertThat(scope.canManageBranch(u.getId(), roles, null)).isFalse();
        assertThat(scope.canManageGroup(u.getId(), roles, null)).isFalse();
        assertThat(scope.canManageGroupMembers(u.getId(), roles, null)).isFalse();
    }

    @Test
    void laFechaDeHoySeTomaEnLaZonaDeCanarias() {
        // 30 de septiembre, 23:30 UTC: en Canarias (UTC+1 en verano) ya son las 00:30 del 1 de octubre
        Clock summerMidnight = Clock.fixed(Instant.parse("2026-09-30T23:30:00Z"), java.time.ZoneOffset.UTC);
        ScopeAccess atMidnight = new ScopeAccess(team, users, summerMidnight);
        Set<Role> roles = Set.of(Role.MEMBER);
        User u = person(roles, null, null);
        // puesto que empieza el 1 de octubre: en Canarias, a las 00:30 del 1 de octubre, ya está vigente
        position(u, castores, TeamPosition.COORDINACION, LocalDate.of(2026, 10, 1), LocalDate.of(2027, 9, 30));

        assertThat(atMidnight.canManageBranch(u.getId(), roles, castores.getId())).isTrue();
        assertThat(scope.canManageBranch(u.getId(), roles, castores.getId())).isTrue();
    }
}
