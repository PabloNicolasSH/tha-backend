package org.scoutsdecanarias.tha_backend.features.mfa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class MfaDtos {

    private MfaDtos() {
    }

    /** Respuesta del login cuando la cuenta tiene segundo factor: aún no hay sesión, solo este desafío. */
    public record MfaChallenge(boolean mfaRequired, String mfaToken, List<MfaMethod> methods, MfaMethod preferredMethod) {
    }

    public record MfaStatus(boolean enabled, List<MfaMethod> methods, MfaMethod preferredMethod,
                            int recoveryCodesLeft, boolean totpSetupPending) {
    }

    /** Alta de la app autenticadora: QR para escanear y, como alternativa, el secreto para teclearlo. */
    public record TotpSetup(String secret, String otpauthUri, String qrDataUri) {
    }

    /** Vacío salvo al activar el primer método: entonces trae los códigos de recuperación (única vez que se ven). */
    public record RecoveryCodes(List<String> recoveryCodes) {
    }

    public record CodeRequest(@NotBlank String code) {
    }

    public record PasswordRequest(@NotBlank String password) {
    }

    public record PreferredRequest(@NotNull MfaMethod method) {
    }

    public record VerifyRequest(@NotBlank String mfaToken, @NotNull MfaCodeType type, @NotBlank String code) {
    }

    public record SendEmailCodeRequest(@NotBlank String mfaToken) {
    }
}
