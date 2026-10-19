package org.scoutsdecanarias.tha_backend.features.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Tokens de un solo uso para que una persona cree su contraseña tras ser aprobada.
 *
 * Viven en memoria (Caffeine) con caducidad fija: si el servidor se reinicia se pierden y hay que
 * reenviar la invitación. En la caché solo está el hash SHA-256 del token, así que un volcado de memoria
 * no da enlaces utilizables. Cada usuario tiene como mucho un token válido: emitir otro invalida el anterior.
 */
@Service
public class PasswordSetupTokenService {

    private final Cache<String, Long> userByTokenHash;
    private final Cache<Long, String> tokenHashByUser;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public PasswordSetupTokenService(InviteProperties properties) {
        this(properties.expiration(), Ticker.systemTicker());
    }

    /** Constructor con reloj propio, para poder probar la caducidad sin esperar. */
    PasswordSetupTokenService(Duration lifetime, Ticker ticker) {
        this.userByTokenHash = Caffeine.newBuilder().expireAfterWrite(lifetime).ticker(ticker).maximumSize(10_000).build();
        this.tokenHashByUser = Caffeine.newBuilder().expireAfterWrite(lifetime).ticker(ticker).maximumSize(10_000).build();
    }

    /** Emite un token nuevo para el usuario e invalida el que tuviera. Devuelve el valor en claro (va solo en el email). */
    public String issueFor(Long userId) {
        String previous = tokenHashByUser.getIfPresent(userId);
        if (previous != null) {
            userByTokenHash.invalidate(previous);
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = hash(raw);
        userByTokenHash.put(hash, userId);
        tokenHashByUser.put(userId, hash);
        return raw;
    }

    /** Comprueba el token sin gastarlo (para avisar de un enlace caducado antes de pedir la contraseña). */
    public Optional<Long> peek(String raw) {
        return raw == null ? Optional.empty() : Optional.ofNullable(userByTokenHash.getIfPresent(hash(raw)));
    }

    /** Gasta el token: es válido una sola vez. */
    public Optional<Long> consume(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String hash = hash(raw);
        Long userId = userByTokenHash.asMap().remove(hash);
        if (userId != null) {
            tokenHashByUser.invalidate(userId);
        }
        return Optional.ofNullable(userId);
    }

    public void revokeFor(Long userId) {
        String hash = tokenHashByUser.asMap().remove(userId);
        if (hash != null) {
            userByTokenHash.invalidate(hash);
        }
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
