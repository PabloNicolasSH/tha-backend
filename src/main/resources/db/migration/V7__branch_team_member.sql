-- Puestos del equipo de gestión de una rama, con vigencia. Una rama puede tener varias personas en un mismo puesto.
CREATE TABLE branch_team_member (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    branch_id   BIGINT      NOT NULL,
    user_id     BIGINT      NOT NULL,
    position    VARCHAR(20) NOT NULL,
    start_date  DATE        NOT NULL,
    end_date    DATE        NOT NULL,
    created_by  BIGINT      NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_branch_team_user (user_id, start_date, end_date),
    KEY idx_branch_team_branch (branch_id, start_date, end_date),
    CONSTRAINT fk_branch_team_branch FOREIGN KEY (branch_id) REFERENCES branch (id),
    CONSTRAINT fk_branch_team_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_branch_team_creator FOREIGN KEY (created_by) REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT chk_branch_team_dates CHECK (end_date >= start_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
