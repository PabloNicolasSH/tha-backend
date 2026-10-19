package org.scoutsdecanarias.tha_backend.features.access;

import org.scoutsdecanarias.tha_backend.core.time.ClockConfig;
import org.scoutsdecanarias.tha_backend.features.branch.BranchTeamMemberRepository;
import org.scoutsdecanarias.tha_backend.features.user.Role;
import org.scoutsdecanarias.tha_backend.features.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Permisos por ámbito: qué grupo o qué rama puede gestionar la persona. Se usa en las anotaciones:
 * {@code @PreAuthorize("@scope.canManageBranch(#branchId)")}.
 *
 * <ul>
 *   <li><b>Federación</b> ({@code ADMIN}, {@code SECRETARY}, {@code TREASURER}, {@code BOARD_MEMBER},
 *       {@code LOGISTICS}): actúa sobre todos los grupos y ramas en lo que le compete.</li>
 *   <li><b>Coordinación de grupo</b> ({@code GROUP_COORDINATOR}): solo sobre <i>su</i> grupo, el que consta en su
 *       cuenta.</li>
 *   <li><b>Equipo de una rama</b>: cualquier persona con un puesto <i>vigente hoy</i> en esa rama (coordinación,
 *       tesorería o secretaría tienen el mismo alcance), y solo sobre esa rama.</li>
 * </ul>
 *
 * Los roles de rama del token no se usan aquí: el puesto se vuelve a consultar con la fecha de hoy en cada
 * comprobación, de modo que un puesto caducado pierde el permiso aunque el token siga vigente (dura 15 minutos).
 * La rama personal de una persona ({@code User.branch}) no concede ningún permiso.
 */
@Component("scope")
public class ScopeAccess {

    private final BranchTeamMemberRepository team;
    private final UserRepository users;
    private final Clock clock;

    public ScopeAccess(BranchTeamMemberRepository team, UserRepository users, Clock clock) {
        this.team = team;
        this.users = users;
        this.clock = clock;
    }

    // ---------- Para anotaciones: usan a la persona autenticada ----------

    public boolean isFederationStaff() {
        return isFederationStaff(currentRoles());
    }

    public boolean canManageGroup(Long groupId) {
        Long userId = currentUserId();
        return userId != null && canManageGroup(userId, currentRoles(), groupId);
    }

    public boolean canManageGroupMembers(Long groupId) {
        Long userId = currentUserId();
        return userId != null && canManageGroupMembers(userId, currentRoles(), groupId);
    }

    public boolean canManageBranch(Long branchId) {
        Long userId = currentUserId();
        return userId != null && canManageBranch(userId, currentRoles(), branchId);
    }

    // ---------- Reglas, con la persona y sus roles explícitos ----------

    public boolean isFederationStaff(Set<Role> roles) {
        return roles.stream().anyMatch(Role::isFederation);
    }

    /** Ver y gestionar un grupo: la federación (en lo que le compete) o la coordinación de ese grupo. */
    public boolean canManageGroup(Long userId, Set<Role> roles, Long groupId) {
        return groupId != null && (isFederationStaff(roles) || isCoordinatorOf(userId, roles, groupId));
    }

    /**
     * Gestionar a las personas de un grupo (desactivar, corregir datos…). En la federación lo hacen administración y
     * secretaría; el resto de roles de federación no administra personas.
     */
    public boolean canManageGroupMembers(Long userId, Set<Role> roles, Long groupId) {
        return groupId != null
                && (roles.contains(Role.ADMIN) || roles.contains(Role.SECRETARY) || isCoordinatorOf(userId, roles, groupId));
    }

    /** Gestionar una rama: la federación o quien tenga un puesto vigente hoy en el equipo de esa rama. */
    public boolean canManageBranch(Long userId, Set<Role> roles, Long branchId) {
        if (branchId == null) {
            return false;
        }
        return isFederationStaff(roles) || team.hasActivePosition(userId, branchId, today());
    }

    private boolean isCoordinatorOf(Long userId, Set<Role> roles, Long groupId) {
        return roles.contains(Role.GROUP_COORDINATOR)
                && users.findScoutGroupId(userId).map(id -> Objects.equals(id, groupId)).orElse(false);
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ClockConfig.ZONE));
    }

    private static Authentication authentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() ? auth : null;
    }

    private static Long currentUserId() {
        Authentication auth = authentication();
        if (auth == null) {
            return null;
        }
        try {
            return Long.valueOf(auth.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Set<Role> currentRoles() {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        Authentication auth = authentication();
        if (auth != null) {
            for (GrantedAuthority authority : auth.getAuthorities()) {
                String name = authority.getAuthority();
                if (name.startsWith("ROLE_")) {
                    try {
                        roles.add(Role.valueOf(name.substring(5)));
                    } catch (IllegalArgumentException ignored) {
                        // un rol desconocido no concede nada
                    }
                }
            }
        }
        return roles;
    }
}
