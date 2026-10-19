package org.scoutsdecanarias.tha_backend.features.mfa;

import jakarta.validation.Valid;
import org.scoutsdecanarias.tha_backend.features.mfa.MfaDtos.CodeRequest;
import org.scoutsdecanarias.tha_backend.features.mfa.MfaDtos.MfaStatus;
import org.scoutsdecanarias.tha_backend.features.mfa.MfaDtos.PasswordRequest;
import org.scoutsdecanarias.tha_backend.features.mfa.MfaDtos.PreferredRequest;
import org.scoutsdecanarias.tha_backend.features.mfa.MfaDtos.RecoveryCodes;
import org.scoutsdecanarias.tha_backend.features.mfa.MfaDtos.SendEmailCodeRequest;
import org.scoutsdecanarias.tha_backend.features.mfa.MfaDtos.TotpSetup;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestión del segundo factor de quien tiene sesión. Los dos endpoints del inicio de sesión
 * ({@code /verify} y {@code /email/send}) están en AuthController porque aún no hay sesión.
 */
@RestController
@RequestMapping("/api/auth/mfa")
public class MfaController {

    private final MfaService mfa;

    public MfaController(MfaService mfa) {
        this.mfa = mfa;
    }

    @GetMapping
    MfaStatus status(@AuthenticationPrincipal Jwt jwt) {
        return mfa.status(userId(jwt));
    }

    @PostMapping("/totp/setup")
    TotpSetup totpSetup(@AuthenticationPrincipal Jwt jwt) {
        return mfa.startTotpSetup(userId(jwt));
    }

    @PostMapping("/totp/confirm")
    RecoveryCodes totpConfirm(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CodeRequest request) {
        return mfa.confirmTotp(userId(jwt), request.code());
    }

    @PostMapping("/email/enable")
    ResponseEntity<Void> emailEnable(@AuthenticationPrincipal Jwt jwt) {
        mfa.startEmailEnable(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email/confirm")
    RecoveryCodes emailConfirm(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CodeRequest request) {
        return mfa.confirmEmail(userId(jwt), request.code());
    }

    @PostMapping("/{method}/disable")
    ResponseEntity<Void> disable(@AuthenticationPrincipal Jwt jwt, @PathVariable MfaMethod method,
                                 @Valid @RequestBody PasswordRequest request) {
        mfa.disable(userId(jwt), method, request.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/recovery-codes")
    RecoveryCodes regenerateRecoveryCodes(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordRequest request) {
        return mfa.regenerateRecoveryCodes(userId(jwt), request.password());
    }

    @PutMapping("/preferred")
    ResponseEntity<Void> preferred(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PreferredRequest request) {
        mfa.setPreferred(userId(jwt), request.method());
        return ResponseEntity.noContent().build();
    }

    private static Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
