package org.scoutsdecanarias.tha_backend.features.mfa;

/** Qué clase de código se presenta al iniciar sesión. RECOVERY es un código de recuperación de un solo uso. */
public enum MfaCodeType {
    TOTP,
    EMAIL,
    RECOVERY
}
