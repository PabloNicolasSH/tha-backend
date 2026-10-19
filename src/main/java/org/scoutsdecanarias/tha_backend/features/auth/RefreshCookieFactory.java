package org.scoutsdecanarias.tha_backend.features.auth;

import org.scoutsdecanarias.tha_backend.core.security.RefreshProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Construye la cookie del refresh token: HttpOnly (JS no la lee), SameSite=Strict y limitada a /api/auth. */
@Component
public class RefreshCookieFactory {

    public static final String NAME = "tha_refresh";
    private static final String PATH = "/api/auth";

    private final RefreshProperties properties;

    public RefreshCookieFactory(RefreshProperties properties) {
        this.properties = properties;
    }

    /**
     * Con "Mantener sesión iniciada" ({@code persistent}) la cookie caduca a los 30 días;
     * sin él es una cookie de sesión que el navegador borra al cerrarse.
     */
    public String issue(String value, boolean persistent) {
        return build(value, persistent ? properties.expiration() : null).toString();
    }

    public String clear() {
        return build("", Duration.ZERO).toString();
    }

    private ResponseCookie build(String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(PATH);
        if (maxAge != null) {
            builder.maxAge(maxAge);
        }
        return builder.build();
    }
}
