package org.scoutsdecanarias.tha_backend.features.mfa;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.scoutsdecanarias.tha_backend.core.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Frena la adivinación de códigos: tras 5 fallos seguidos se bloquea el segundo factor de esa cuenta
 * durante 15 minutos (aunque el atacante tenga la contraseña). Un acierto reinicia el contador.
 * Vive en memoria: se reinicia con el servidor, que es aceptable para una ventana de 15 minutos.
 */
@Component
public class MfaAttemptLimiter {

    static final int MAX_FAILURES = 5;

    private final Cache<Long, AtomicInteger> failures = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(10_000)
            .build();

    public void checkAllowed(Long userId) {
        AtomicInteger count = failures.getIfPresent(userId);
        if (count != null && count.get() >= MAX_FAILURES) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Demasiados intentos fallidos. Espera unos minutos antes de volver a probar");
        }
    }

    public void recordFailure(Long userId) {
        failures.asMap().computeIfAbsent(userId, id -> new AtomicInteger()).incrementAndGet();
    }

    public void reset(Long userId) {
        failures.invalidate(userId);
    }
}
