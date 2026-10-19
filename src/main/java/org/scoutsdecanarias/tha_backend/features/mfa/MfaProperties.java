package org.scoutsdecanarias.tha_backend.features.mfa;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param encryptionKey clave AES-256 (32 bytes en Base64) con la que se cifran los secretos TOTP
 * @param issuer        nombre que muestra la app autenticadora
 * @param emailCodeTtl  validez del código enviado por correo
 * @param tokenTtl      validez del token temporal entre la contraseña y el segundo factor
 */
@ConfigurationProperties(prefix = "tha.mfa")
public record MfaProperties(String encryptionKey, String issuer, Duration emailCodeTtl, Duration tokenTtl) {
}
