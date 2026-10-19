package org.scoutsdecanarias.tha_backend.features.scout_group;

import org.scoutsdecanarias.tha_backend.core.exception.ApiException;
import org.scoutsdecanarias.tha_backend.features.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScoutGroupService {

    public record GroupInfo(Long id, String name, String email, String island, boolean active, long members,
                            Instant createdAt) {
    }

    /** Solo lo que necesita el formulario de solicitud de cuenta: sin email ni isla. */
    public record PublicGroup(Long id, String name) {
    }

    public record GroupInput(String name, String email, String island) {
    }

    private final ScoutGroupRepository groups;
    private final UserRepository users;

    public ScoutGroupService(ScoutGroupRepository groups, UserRepository users) {
        this.groups = groups;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<GroupInfo> list() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : groups.countMembersByGroup()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return groups.findAllByOrderByNameAsc().stream()
                .map(g -> toInfo(g, counts.getOrDefault(g.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PublicGroup> activeGroups() {
        return groups.findByActiveTrueOrderByNameAsc().stream()
                .map(g -> new PublicGroup(g.getId(), g.getName()))
                .toList();
    }

    @Transactional
    public GroupInfo create(GroupInput input) {
        String name = input.name().trim();
        if (groups.existsByNameIgnoreCase(name)) {
            throw duplicated();
        }
        ScoutGroup group = new ScoutGroup();
        apply(group, name, input);
        return toInfo(groups.save(group), 0);
    }

    @Transactional
    public GroupInfo update(Long id, GroupInput input) {
        ScoutGroup group = find(id);
        String name = input.name().trim();
        if (groups.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicated();
        }
        apply(group, name, input);
        return toInfo(group, users.countByScoutGroupId(id));
    }

    /** Desactivar o reactivar: el grupo conserva a sus personas pero deja de ofrecerse en el registro. */
    @Transactional
    public GroupInfo setActive(Long id, boolean active) {
        ScoutGroup group = find(id);
        group.setActive(active);
        return toInfo(group, users.countByScoutGroupId(id));
    }

    @Transactional
    public void delete(Long id) {
        ScoutGroup group = find(id);
        if (users.countByScoutGroupId(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "El grupo tiene personas asignadas: no se puede borrar, solo desactivar");
        }
        groups.delete(group);
    }

    private ScoutGroup find(Long id) {
        return groups.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "El grupo no existe"));
    }

    private void apply(ScoutGroup group, String name, GroupInput input) {
        group.setName(name);
        String email = input.email() == null ? "" : input.email().trim();
        group.setEmail(email.isEmpty() ? null : email);
        String island = input.island() == null ? "" : input.island().trim();
        if (island.isEmpty()) {
            group.setIsland(null);
        } else {
            group.setIsland(Island.parse(island)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "La isla indicada no es válida"))
                    .name());
        }
    }

    private static ApiException duplicated() {
        return new ApiException(HttpStatus.CONFLICT, "Ya existe un grupo con ese nombre");
    }

    private static GroupInfo toInfo(ScoutGroup g, long members) {
        return new GroupInfo(g.getId(), g.getName(), g.getEmail(), g.getIsland(), g.isActive(), members,
                g.getCreatedAt());
    }
}
