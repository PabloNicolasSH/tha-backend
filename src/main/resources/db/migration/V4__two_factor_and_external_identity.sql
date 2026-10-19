-- Segundo factor de cada usuario. El secreto TOTP se guarda cifrado (AES-GCM), nunca en claro.
CREATE TABLE user_mfa (
    user_id          BIGINT      NOT NULL,
    totp_secret_enc  VARCHAR(255),
    totp_confirmed   BOOLEAN     NOT NULL DEFAULT FALSE,
    -- último intervalo de 30 s aceptado: impide reutilizar un código TOTP ya gastado
    totp_last_step   BIGINT,
    email_enabled    BOOLEAN     NOT NULL DEFAULT FALSE,
    preferred_method VARCHAR(10),
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_mfa_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Códigos de recuperación de un solo uso (solo su hash SHA-256).
CREATE TABLE mfa_recovery_code (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BIGINT      NOT NULL,
    code_hash  CHAR(64)    NOT NULL,
    used_at    DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_mfa_recovery_hash (code_hash),
    KEY idx_mfa_recovery_user (user_id),
    CONSTRAINT fk_mfa_recovery_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Identidad en un proveedor SSO (OIDC) enlazada a una cuenta de Tha.
CREATE TABLE external_identity (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    provider   VARCHAR(60)  NOT NULL,
    subject    VARCHAR(255) NOT NULL,
    email      VARCHAR(255),
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_external_identity (provider, subject),
    KEY idx_external_identity_user (user_id),
    CONSTRAINT fk_external_identity_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
