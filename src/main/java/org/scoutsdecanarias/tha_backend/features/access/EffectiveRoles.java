package org.scoutsdecanarias.tha_backend.features.access;

import org.scoutsdecanarias.tha_backend.core.time.ClockConfig;
import org.scoutsdecanarias.tha_backend.features.branch.BranchTeamMemberRepository;
import org.scoutsdecanarias.tha_backend.features.branch.TeamPosition;
import org.scoutsdecanarias.tha_backend.features.user.Role;
import org.scoutsdecanarias.tha_backend.features.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/**
 * Los roles que tiene una persona ahora: los que se guardan más los de rama que se derivan de sus puestos vigentes
 * hoy. Se calculan al emitir el token, así que un puesto caduca solo; los permisos por ámbito
 * ({@link ScopeAccess}) lo vuelven a comprobar en cada petición.
 */
@Service
public class EffectiveRoles {

    private final BranchTeamMemberRepository team;
    private final Clock clock;

    public EffectiveRoles(BranchTeamMemberRepository team, Clock clock) {
        this.team = team;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Set<Role> of(User user) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        user.getRoles().stream().filter(r -> !r.isDerived()).forEach(roles::add);
        LocalDate today = LocalDate.now(clock.withZone(ClockConfig.ZONE));
        for (TeamPosition position : team.activePositions(user.getId(), today)) {
            roles.add(position.role());
        }
        return roles;
    }
}
