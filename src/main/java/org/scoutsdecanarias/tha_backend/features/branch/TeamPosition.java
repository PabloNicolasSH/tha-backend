package org.scoutsdecanarias.tha_backend.features.branch;

import org.scoutsdecanarias.tha_backend.features.user.Role;

/** Los tres puestos del equipo de gestión de una rama y el rol que da cada uno mientras está vigente. */
public enum TeamPosition {
    COORDINACION(Role.BRANCH_COORDINATOR),
    TESORERIA(Role.BRANCH_TREASURER),
    SECRETARIA(Role.BRANCH_SECRETARY);

    private final Role role;

    TeamPosition(Role role) {
        this.role = role;
    }

    public Role role() {
        return role;
    }
}
