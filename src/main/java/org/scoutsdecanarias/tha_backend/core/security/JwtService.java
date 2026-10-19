package org.scoutsdecanarias.tha_backend.core.security;

import org.scoutsdecanarias.tha_backend.core.exception.ApiException;
import org.scoutsdecanarias.tha_backend.features.mfa.MfaProperties;
import org.scoutsdecanarias.tha_backend.features.user.Role;
import org.scoutsdecanarias.tha_backend.features.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;

/**
 * Emite y lee los JWT de Tha: el access token (corto) y el token temporal que une la contraseña con el
 * segundo factor. El refresh token vive en {@code RefreshTokenService}.
 */
@Service
public class JwtService {

    /** Quién es y si pidió "Mantener sesión iniciada", tras superar la contraseña. */
    public record MfaTokenData(Long userId, boolean remember) {
    }

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final MfaProperties mfaProperties;
    private final JwtDecoder mfaDecoder;

    public JwtService(JwtEncoder encoder, JwtProperties properties, MfaProperties mfaProperties, SecretKey key) {
        this.encoder = encoder;
        this.properties = properties;
        this.mfaProperties = mfaProperties;
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.issuer()), new TokenTypeValidator(TokenTypeValidator.MFA)));
        this.mfaDecoder = decoder;
    }

    public String issueAccessToken(User user) {
        Instant now = Instant.now();
        return encode(JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(properties.expiration()))
                .claim(TokenTypeValidator.CLAIM, TokenTypeValidator.ACCESS)
                .claim("roles", user.getRoles().stream().map(Role::name).toList())
                .build());
    }

    /** Token de 5 min que solo vale para completar el segundo factor. No da acceso a ningún recurso. */
    public String issueMfaToken(User user, boolean remember) {
        Instant now = Instant.now();
        return encode(JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(mfaProperties.tokenTtl()))
                .claim(TokenTypeValidator.CLAIM, TokenTypeValidator.MFA)
                .claim("remember", remember)
                .build());
    }

    public MfaTokenData parseMfaToken(String token) {
        try {
            Jwt jwt = mfaDecoder.decode(token);
            return new MfaTokenData(Long.valueOf(jwt.getSubject()), Boolean.TRUE.equals(jwt.getClaim("remember")));
        } catch (JwtException | NumberFormatException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "La verificación ha caducado. Vuelve a iniciar sesión");
        }
    }

    public Duration accessTokenLifetime() {
        return properties.expiration();
    }

    private String encode(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
