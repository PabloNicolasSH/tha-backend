package org.scoutsdecanarias.tha_backend.core.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param expiration        duración con "Mantener sesión iniciada" (cookie persistente)
 * @param sessionExpiration duración sin marcarlo (cookie de sesión: se borra al cerrar el navegador)
 */
@ConfigurationProperties(prefix = "tha.refresh")
public record RefreshProperties(Duration expiration, Duration sessionExpiration, boolean cookieSecure) {
}
