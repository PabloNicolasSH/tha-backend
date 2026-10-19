package org.scoutsdecanarias.tha_backend.features.auth;

import org.scoutsdecanarias.tha_backend.core.exception.ApiException;
import org.scoutsdecanarias.tha_backend.core.security.RefreshProperties;
import org.scoutsdecanarias.tha_backend.features.user.User;
import org.scoutsdecanarias.tha_backend.features.user.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Refresh tokens opacos con rotación: cada uso entrega uno nuevo e invalida el anterior.
 * Si se presenta un token que ya fue usado (o revocado), se asume robo y se revoca toda la familia.
 */
@Service
public class RefreshTokenService {

    /**
     * Token nuevo en claro (para la cookie) con su usuario.
     * {@code persistent} decide si la cookie lleva caducidad (mantener sesión) o es de sesión.
     */
    public record Issued(String rawToken, User user, boolean persistent) {
    }

    private final RefreshTokenRepository repository;
    private final RefreshProperties properties;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, RefreshProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** Inicia una sesión nueva (login): crea una familia nueva. */
    @Transactional
    public Issued startSession(User user, boolean rememberMe) {
        return create(user, UUID.randomUUID().toString(), rememberMe);
    }

    /** Canjea un refresh token por otro nuevo de la misma familia, conservando "mantener sesión". */
    @Transactional(noRollbackFor = ApiException.class)
    public Issued rotate(String rawToken) {
        RefreshToken token = repository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> unauthorized("Sesión no válida"));
        Instant now = Instant.now();

        if (token.getUsedAt() != null || token.getRevokedAt() != null) {
            repository.revokeFamily(token.getFamilyId(), now);
            throw unauthorized("Sesión no válida");
        }
        if (token.getExpiresAt().isBefore(now)) {
            throw unauthorized("La sesión ha caducado");
        }
        User user = token.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            repository.revokeFamily(token.getFamilyId(), now);
            throw unauthorized("Sesión no válida");
        }

        token.setUsedAt(now);
        return create(user, token.getFamilyId(), token.isPersistent());
    }

    /** Cierra la sesión (logout) revocando la familia del token recibido. */
    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), Instant.now()));
    }

    @Transactional
    public void revokeAllFor(User user) {
        repository.revokeAllForUser(user.getId(), Instant.now());
    }

    private Issued create(User user, String familyId, boolean persistent) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setFamilyId(familyId);
        token.setTokenHash(hash(raw));
        token.setPersistent(persistent);
        token.setExpiresAt(Instant.now().plus(persistent ? properties.expiration() : properties.sessionExpiration()));
        repository.save(token);
        return new Issued(raw, user, persistent);
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, message);
    }
}
