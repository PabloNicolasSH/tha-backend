package org.scoutsdecanarias.tha_backend.features.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;
import org.scoutsdecanarias.tha_backend.features.user.User;

import java.time.Instant;

@Getter
@Setter
@Entity
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 36)
    private String familyId;

    /** SHA-256 en hexadecimal del token. El valor en claro solo lo conoce el navegador. */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    /** "Mantener sesión iniciada". Se hereda en cada rotación. */
    @Column(nullable = false)
    private boolean persistent = true;

    /** Se rellena cuando el token se canjea por uno nuevo (rotación). */
    private Instant usedAt;

    private Instant revokedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
