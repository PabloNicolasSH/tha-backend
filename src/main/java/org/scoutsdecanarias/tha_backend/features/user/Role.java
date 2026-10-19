package org.scoutsdecanarias.tha_backend.features.user;

public enum Role {
    MEMBER,
    /** Coordinación de un grupo scout: se guarda y va ligada al grupo de la persona. */
    GROUP_COORDINATOR,
    MODERATOR,
    SECRETARY,
    TREASURER,
    LOGISTICS,
    /** Junta directiva. */
    BOARD_MEMBER,
    ADMIN,

    // Roles de rama: NO se guardan. Se derivan del puesto vigente en el equipo de gestión de una rama (ver EffectiveRoles).
    BRANCH_COORDINATOR,
    BRANCH_TREASURER,
    BRANCH_SECRETARY;

    /** Los roles de la federación actúan sobre todos los grupos y ramas en lo que les compete. */
    public boolean isFederation() {
        return this == ADMIN || this == SECRETARY || this == TREASURER || this == BOARD_MEMBER || this == LOGISTICS;
    }

    /** Roles derivados de un puesto de rama: nunca se asignan a mano ni se guardan. */
    public boolean isDerived() {
        return this == BRANCH_COORDINATOR || this == BRANCH_TREASURER || this == BRANCH_SECRETARY;
    }
}
