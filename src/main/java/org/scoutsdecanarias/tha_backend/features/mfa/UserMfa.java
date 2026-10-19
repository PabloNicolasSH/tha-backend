package org.scoutsdecanarias.tha_backend.features.mfa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
public class UserMfa {

    @Id
    private Long userId;

    /** Secreto TOTP (bytes en Base64) cifrado con AES-GCM. */
    private String totpSecretEnc;

    /** Falso mientras la persona no haya demostrado con un código que su app lo tiene bien configurado. */
    private boolean totpConfirmed;

    private Long totpLastStep;

    private boolean emailEnabled;

    @Enumerated(EnumType.STRING)
    private MfaMethod preferredMethod;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public UserMfa() {
    }

    public UserMfa(Long userId) {
        this.userId = userId;
    }

    public boolean totpActive() {
        return totpConfirmed && totpSecretEnc != null;
    }

    public boolean anyActive() {
        return totpActive() || emailEnabled;
    }
}
