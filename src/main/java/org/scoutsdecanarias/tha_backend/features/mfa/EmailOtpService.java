package org.scoutsdecanarias.tha_backend.features.mfa;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.scoutsdecanarias.tha_backend.core.exception.ApiException;
import org.scoutsdecanarias.tha_backend.core.mail.MailService;
import org.scoutsdecanarias.tha_backend.features.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Segundo factor por correo: código de 6 dígitos válido unos minutos (Caffeine), de un solo uso, con un
 * máximo de 5 intentos y una espera de 30 s entre envíos. En memoria solo está su hash.
 */
@Service
public class EmailOtpService {

    private static final int MAX_ATTEMPTS = 5;

    private record Pending(String hash, AtomicInteger attempts) {
    }

    private final Cache<Long, Pending> codes;
    private final Cache<Long, Boolean> cooldown = Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).build();
    private final MailService mail;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public EmailOtpService(MailService mail, MfaProperties properties) {
        this.mail = mail;
        this.ttl = properties.emailCodeTtl();
        this.codes = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(10_000).build();
    }

    /** Genera y envía un código nuevo (el anterior deja de valer). */
    public void send(User user) {
        if (cooldown.getIfPresent(user.getId()) != null) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Espera unos segundos antes de pedir otro código");
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        codes.put(user.getId(), new Pending(hash(user.getId(), code), new AtomicInteger()));
        cooldown.put(user.getId(), Boolean.TRUE);
        mail.sendLoginCode(user.getEmail(), user.getName(), code, ttl.toMinutes());
    }

    /** Comprueba y gasta el código. Tras 5 intentos fallidos hay que pedir uno nuevo. */
    public boolean verify(Long userId, String code) {
        Pending pending = codes.getIfPresent(userId);
        if (pending == null || code == null) {
            return false;
        }
        if (pending.attempts().incrementAndGet() > MAX_ATTEMPTS) {
            codes.invalidate(userId);
            return false;
        }
        boolean ok = MessageDigest.isEqual(hash(userId, code).getBytes(StandardCharsets.UTF_8),
                pending.hash().getBytes(StandardCharsets.UTF_8));
        if (ok) {
            codes.invalidate(userId);
            // El código ya está gastado: si hace falta otro (p. ej. el login justo tras activarlo), se puede enviar ya.
            cooldown.invalidate(userId);
        }
        return ok;
    }

    private static String hash(Long userId, String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((userId + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
