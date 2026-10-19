package org.scoutsdecanarias.tha_backend.features.auth;

import org.scoutsdecanarias.tha_backend.core.exception.ApiException;
import org.scoutsdecanarias.tha_backend.core.security.JwtService;
import org.scoutsdecanarias.tha_backend.features.auth.AuthDtos.AuthResponse;
import org.scoutsdecanarias.tha_backend.features.auth.AuthDtos.LoginRequest;
import org.scoutsdecanarias.tha_backend.features.auth.AuthDtos.RegisterRequest;
import org.scoutsdecanarias.tha_backend.features.auth.AuthDtos.SetPasswordRequest;
import org.scoutsdecanarias.tha_backend.features.auth.AuthDtos.UserDto;
import org.scoutsdecanarias.tha_backend.features.mfa.MfaDtos.MfaChallenge;
import org.scoutsdecanarias.tha_backend.features.mfa.MfaDtos.VerifyRequest;
import org.scoutsdecanarias.tha_backend.features.mfa.MfaService;
import org.scoutsdecanarias.tha_backend.features.scout_group.ScoutGroupRepository;
import org.scoutsdecanarias.tha_backend.features.user.User;
import org.scoutsdecanarias.tha_backend.features.user.UserRepository;
import org.scoutsdecanarias.tha_backend.features.user.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    /** Resultado de abrir o renovar sesión: lo que va en el cuerpo y lo que va en la cookie. */
    public record Session(AuthResponse body, String refreshToken, boolean persistent) {
    }

    /** Tras la contraseña: o hay sesión, o falta el segundo factor ({@code challenge}). Nunca las dos. */
    public record LoginOutcome(Session session, MfaChallenge challenge) {
    }

    private static final String INVALID_LINK = "El enlace no es válido o ha caducado";

    // Hash de una contraseña inexistente: se compara igualmente si el email no existe o la cuenta aún no
    // tiene contraseña, para que el tiempo de respuesta no delate qué emails tienen cuenta.
    private final String dummyHash;

    private final UserRepository users;
    private final ScoutGroupRepository groups;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final RefreshTokenService refreshTokens;
    private final PasswordSetupTokenService setupTokens;
    private final MfaService mfa;

    public AuthService(UserRepository users, ScoutGroupRepository groups, PasswordEncoder passwordEncoder,
                       JwtService jwt, RefreshTokenService refreshTokens, PasswordSetupTokenService setupTokens,
                       MfaService mfa) {
        this.users = users;
        this.groups = groups;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
        this.setupTokens = setupTokens;
        this.mfa = mfa;
        this.dummyHash = passwordEncoder.encode("no-existe-esta-contraseña");
    }

    /** Solicitud de cuenta: sin contraseña. Queda pendiente hasta que la federación la apruebe. */
    @Transactional
    public void register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Ya existe una cuenta o una solicitud con ese email");
        }
        User user = new User();
        user.setEmail(email);
        user.setName(request.name().trim());
        user.setSurname(request.surname().trim());
        user.setStatus(UserStatus.PENDING);
        if (request.scoutGroupId() != null) {
            user.setScoutGroup(groups.findById(request.scoutGroupId())
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "El grupo indicado no existe")));
        }
        users.save(user);
    }

    @Transactional
    public LoginOutcome login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        boolean hasPassword = user != null && user.getPasswordHash() != null;
        boolean passwordOk = passwordEncoder.matches(request.password(), hasPassword ? user.getPasswordHash() : dummyHash)
                && hasPassword;

        if (user != null && !hasPassword) {
            // Sin contraseña todavía: se explica en qué punto está, pero no se revela nada más.
            throw switch (user.getStatus()) {
                case PENDING -> new ApiException(HttpStatus.FORBIDDEN, "Tu solicitud está pendiente de aprobación por la federación");
                case INVITED -> new ApiException(HttpStatus.FORBIDDEN, "Revisa tu email: te enviamos un enlace para crear tu contraseña");
                default -> new ApiException(HttpStatus.UNAUTHORIZED, "Email o contraseña incorrectos");
            };
        }
        if (user == null || !passwordOk) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email o contraseña incorrectos");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Tu cuenta está desactivada");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Tu cuenta aún no está activa");
        }
        // Contraseña correcta. Si la cuenta tiene segundo factor, aún no hay sesión: se pide el código.
        if (mfa.isEnabled(user.getId())) {
            return new LoginOutcome(null, mfa.challenge(user, request.remember()));
        }
        return new LoginOutcome(session(refreshTokens.startSession(user, request.remember())), null);
    }

    /** Segundo paso del login: con el código correcto se abre la sesión. */
    @Transactional
    public Session verifyMfa(VerifyRequest request) {
        MfaService.Verified verified = mfa.verifyLogin(request.mfaToken(), request.type(), request.code());
        return session(refreshTokens.startSession(verified.user(), verified.remember()));
    }

    public void sendMfaEmailCode(String mfaToken) {
        mfa.sendEmailCode(mfaToken);
    }

    public Session refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "No hay sesión iniciada");
        }
        return session(refreshTokens.rotate(rawRefreshToken));
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokens.revoke(rawRefreshToken);
        }
    }

    /** ¿Sigue siendo válido el enlace de invitación? (la página lo consulta antes de pedir la contraseña). */
    public boolean isSetupTokenValid(String token) {
        return setupTokens.peek(token).isPresent();
    }

    /** Crea la contraseña con el enlace de la invitación. El token se gasta, y las sesiones anteriores se cierran. */
    @Transactional
    public void setPassword(SetPasswordRequest request) {
        Long userId = setupTokens.consume(request.token())
                .orElseThrow(() -> new ApiException(HttpStatus.GONE, INVALID_LINK));
        User user = users.findById(userId).orElseThrow(() -> new ApiException(HttpStatus.GONE, INVALID_LINK));
        if (user.getStatus() != UserStatus.INVITED && user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.GONE, INVALID_LINK);
        }
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        refreshTokens.revokeAllFor(user);
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return users.findById(userId)
                .map(UserDto::from)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sesión no válida"));
    }

    private Session session(RefreshTokenService.Issued issued) {
        AuthResponse body = new AuthResponse(jwt.issueAccessToken(issued.user()),
                jwt.accessTokenLifetime().toSeconds(), UserDto.from(issued.user()));
        return new Session(body, issued.rawToken(), issued.persistent());
    }
}
