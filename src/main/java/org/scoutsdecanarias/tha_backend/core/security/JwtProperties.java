package org.scoutsdecanarias.tha_backend.core.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tha.jwt")
public record JwtProperties(String secret, String issuer, Duration expiration) {
}
