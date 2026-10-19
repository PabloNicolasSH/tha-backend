package org.scoutsdecanarias.tha_backend.core.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Cada JWT que emite Tha lleva un claim "typ" ("access" o "mfa"). Así un token temporal del segundo factor
 * (que se entrega con solo la contraseña) nunca puede usarse como access token, aunque esté bien firmado.
 */
public final class TokenTypeValidator implements OAuth2TokenValidator<Jwt> {

    public static final String CLAIM = "typ";
    public static final String ACCESS = "access";
    public static final String MFA = "mfa";

    private final String expected;

    public TokenTypeValidator(String expected) {
        this.expected = expected;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (expected.equals(jwt.getClaimAsString(CLAIM))) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Tipo de token no válido", null));
    }
}
