package org.scoutsdecanarias.tha_backend.features.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "tha.invite")
public record InviteProperties(Duration expiration) {
}
