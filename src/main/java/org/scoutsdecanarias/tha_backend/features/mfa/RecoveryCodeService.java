package org.scoutsdecanarias.tha_backend.features.mfa;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Códigos de recuperación: 10 de un solo uso para entrar si se pierde el móvil. Solo se guarda su hash. */
@Service
public class RecoveryCodeService {

    private static final int COUNT = 10;
    // Alfabeto sin caracteres que se confunden (0/O, 1/I/L)
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private final MfaRecoveryCodeRepository repository;
    private final SecureRandom random = new SecureRandom();

    public RecoveryCodeService(MfaRecoveryCodeRepository repository) {
        this.repository = repository;
    }

    /** Sustituye los códigos anteriores por 10 nuevos y los devuelve en claro. Es la única vez que se ven. */
    @Transactional
    public List<String> regenerate(Long userId) {
        repository.deleteAllForUser(userId);
        List<String> plain = new ArrayList<>(COUNT);
        for (int i = 0; i < COUNT; i++) {
            String code = randomGroup() + "-" + randomGroup();
            MfaRecoveryCode entity = new MfaRecoveryCode();
            entity.setUserId(userId);
            entity.setCodeHash(hash(code));
            repository.save(entity);
            plain.add(code);
        }
        return plain;
    }

    /** Gasta un código si es válido y no se había usado. */
    @Transactional
    public boolean consume(Long userId, String code) {
        return repository.findByUserIdAndCodeHashAndUsedAtIsNull(userId, hash(code))
                .map(c -> {
                    c.setUsedAt(Instant.now());
                    return true;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public int remaining(Long userId) {
        return (int) repository.countByUserIdAndUsedAtIsNull(userId);
    }

    @Transactional
    public void deleteAll(Long userId) {
        repository.deleteAllForUser(userId);
    }

    private String randomGroup() {
        StringBuilder sb = new StringBuilder(5);
        for (int i = 0; i < 5; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Se ignoran mayúsculas, espacios y guiones al comparar. */
    private static String hash(String code) {
        String normalized = code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
