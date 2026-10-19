package org.scoutsdecanarias.tha_backend.features.auth;

import jakarta.validation.Valid;
import org.scoutsdecanarias.tha_backend.features.auth.AuthDtos.AuthResponse;
import org.scoutsdecanarias.tha_backend.features.auth.AuthDtos.LoginRequest;
import org.scoutsdecanarias.tha_backend.features.auth.AuthDtos.RegisterRequest;
import org.scoutsdecanarias.tha_backend.features.auth.AuthDtos.SetPasswordRequest;
import org.scoutsdecanarias.tha_backend.features.auth.AuthDtos.UserDto;
import org.scoutsdecanarias.tha_backend.features.mfa.MfaDtos.SendEmailCodeRequest;
import org.scoutsdecanarias.tha_backend.features.mfa.MfaDtos.VerifyRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final RefreshCookieFactory cookies;

    public AuthController(AuthService auth, RefreshCookieFactory cookies) {
        this.auth = auth;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        auth.register(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    /**
     * Devuelve la sesión (access token en el cuerpo y refresh token en cookie) o, si la cuenta tiene segundo
     * factor, un desafío {@code {mfaRequired: true, mfaToken, methods, preferredMethod}} sin cookie.
     */
    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginOutcome outcome = auth.login(request);
        return outcome.challenge() != null ? ResponseEntity.ok(outcome.challenge()) : withRefreshCookie(outcome.session());
    }

    /** Segundo paso del login: código de la app, del correo o de recuperación. */
    @PostMapping("/mfa/verify")
    ResponseEntity<AuthResponse> verifyMfa(@Valid @RequestBody VerifyRequest request) {
        return withRefreshCookie(auth.verifyMfa(request));
    }

    /** Reenvía el código por correo durante el login. */
    @PostMapping("/mfa/email/send")
    ResponseEntity<Void> sendMfaEmail(@Valid @RequestBody SendEmailCodeRequest request) {
        auth.sendMfaEmailCode(request.mfaToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(@CookieValue(name = RefreshCookieFactory.NAME, required = false) String refreshToken) {
        return withRefreshCookie(auth.refresh(refreshToken));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = RefreshCookieFactory.NAME, required = false) String refreshToken) {
        auth.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear()).build();
    }

    /** 204 si el enlace de invitación sigue siendo válido, 410 si no. No gasta el token. */
    @GetMapping("/set-password/check")
    ResponseEntity<Void> checkSetPasswordLink(@RequestParam String token) {
        return auth.isSetupTokenValid(token)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.GONE).build();
    }

    @PostMapping("/set-password")
    ResponseEntity<Void> setPassword(@Valid @RequestBody SetPasswordRequest request) {
        auth.setPassword(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    UserDto me(@AuthenticationPrincipal Jwt jwt) {
        return auth.me(Long.valueOf(jwt.getSubject()));
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthService.Session session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(session.refreshToken(), session.persistent()))
                .body(session.body());
    }
}
