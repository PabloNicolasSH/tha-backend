package org.scoutsdecanarias.tha_backend.features.sso;

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

/** Una cuenta de Tha enlazada a una identidad de un proveedor SSO (proveedor + "sub" estable del proveedor). */
@Getter
@Setter
@Entity
public class ExternalIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String subject;

    private String email;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
