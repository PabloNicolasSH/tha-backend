package org.scoutsdecanarias.tha_backend.features.mfa;

/** Métodos de segundo factor que la persona puede activar. */
public enum MfaMethod {
    /** App autenticadora (Google Authenticator, Microsoft Authenticator, Aegis, 1Password…). */
    TOTP,
    /** Código de 6 dígitos enviado al correo electrónico. */
    EMAIL
}
