package org.scoutsdecanarias.tha_backend.features.mfa;

import org.scoutsdecanarias.tha_backend.core.exception.ApiException;
import org.scoutsdecanarias.tha_backend.core.security.JwtService;
import org.scoutsdecanarias.tha_backend.core.security.SecretCipher;
import org.scoutsdecanarias.tha_backend.features.mfa.MfaDtos.MfaChallenge;
import org.scoutsdecanarias.tha_backend.features.mfa.MfaDtos.MfaStatus;
import org.scoutsdecanarias.tha_backend.features.mfa.MfaDtos.RecoveryCodes;
import org.scoutsdecanarias.tha_backend.features.mfa.MfaDtos.TotpSetup;
import org.scoutsdecanarias.tha_backend.features.user.User;
import org.scoutsdecanarias.tha_backend.features.user.UserRepository;
import org.scoutsdecanarias.tha_backend.features.user.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

@Service
public class MfaService {

    /** Quién ha superado el segundo factor y si pidió mantener la sesión. */
    public record Verified(User user, boolean remember) {
    }

    private final UserRepository users;
    private final UserMfaRepository mfaRepository;
    private final TotpService totp;
    private final EmailOtpService emailOtp;
    private final RecoveryCodeService recovery;
    private final SecretCipher cipher;
    private final MfaAttemptLimiter limiter;
    private final JwtService jwt;
    private final PasswordEncoder passwordEncoder;

    public MfaService(UserRepository users, UserMfaRepository mfaRepository, TotpService totp, EmailOtpService emailOtp,
                      RecoveryCodeService recovery, SecretCipher cipher, MfaAttemptLimiter limiter, JwtService jwt,
                      PasswordEncoder passwordEncoder) {
        this.users = users;
        this.mfaRepository = mfaRepository;
        this.totp = totp;
        this.emailOtp = emailOtp;
        this.recovery = recovery;
        this.cipher = cipher;
        this.limiter = limiter;
        this.jwt = jwt;
        this.passwordEncoder = passwordEncoder;
    }

    // ---------- Inicio de sesión ----------

    @Transactional(readOnly = true)
    public boolean isEnabled(Long userId) {
        return mfaRepository.findById(userId).map(UserMfa::anyActive).orElse(false);
    }

    /** Tras la contraseña: entrega el token temporal y los métodos disponibles. Si el preferido es el correo, envía ya el código. */
    @Transactional
    public MfaChallenge challenge(User user, boolean remember) {
        UserMfa mfa = mfaRepository.findById(user.getId()).orElseThrow();
        List<MfaMethod> methods = methodsOf(mfa);
        MfaMethod preferred = methods.contains(mfa.getPreferredMethod()) ? mfa.getPreferredMethod() : methods.getFirst();
        if (preferred == MfaMethod.EMAIL) {
            try {
                emailOtp.send(user);
            } catch (ApiException tooSoon) {
                // Ya se envió uno hace un momento (el de un intento anterior sigue valiendo): no impide el login.
            }
        }
        return new MfaChallenge(true, jwt.issueMfaToken(user, remember), methods, preferred);
    }

    /** "Reenviar código" durante el inicio de sesión. */
    @Transactional
    public void sendEmailCode(String mfaToken) {
        User user = userFrom(jwt.parseMfaToken(mfaToken));
        UserMfa mfa = mfaRepository.findById(user.getId()).orElseThrow(() -> unauthorized());
        if (!mfa.isEmailEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El código por correo no está activado en esta cuenta");
        }
        limiter.checkAllowed(user.getId());
        emailOtp.send(user);
    }

    @Transactional
    public Verified verifyLogin(String mfaToken, MfaCodeType type, String code) {
        JwtService.MfaTokenData data = jwt.parseMfaToken(mfaToken);
        User user = userFrom(data);
        limiter.checkAllowed(user.getId());
        UserMfa mfa = mfaRepository.findById(user.getId()).orElseThrow(() -> unauthorized());

        boolean ok = switch (type) {
            case TOTP -> verifyTotp(mfa, code);
            case EMAIL -> mfa.isEmailEnabled() && emailOtp.verify(user.getId(), code.trim());
            case RECOVERY -> recovery.consume(user.getId(), code);
        };
        if (!ok) {
            limiter.recordFailure(user.getId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Código incorrecto");
        }
        limiter.reset(user.getId());
        return new Verified(user, data.remember());
    }

    // ---------- Gestión desde "Seguridad de la cuenta" ----------

    @Transactional(readOnly = true)
    public MfaStatus status(Long userId) {
        UserMfa mfa = mfaRepository.findById(userId).orElse(null);
        if (mfa == null) {
            return new MfaStatus(false, List.of(), null, 0, false);
        }
        List<MfaMethod> methods = methodsOf(mfa);
        MfaMethod preferred = methods.isEmpty() ? null
                : methods.contains(mfa.getPreferredMethod()) ? mfa.getPreferredMethod() : methods.getFirst();
        boolean pending = mfa.getTotpSecretEnc() != null && !mfa.isTotpConfirmed();
        return new MfaStatus(!methods.isEmpty(), methods, preferred, recovery.remaining(userId), pending);
    }

    /** Paso 1 del alta de la app autenticadora: genera el secreto (aún sin activar) y su QR. */
    @Transactional
    public TotpSetup startTotpSetup(Long userId) {
        User user = findUser(userId);
        UserMfa mfa = mfaOf(userId);
        if (mfa.totpActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "La app autenticadora ya está activada. Desactívala antes de configurarla de nuevo");
        }
        String secret = totp.newSecret();
        mfa.setTotpSecretEnc(cipher.encrypt(secret));
        mfa.setTotpConfirmed(false);
        mfa.setTotpLastStep(null);
        String uri = totp.otpauthUri(secret, user.getEmail());
        return new TotpSetup(totp.base32(secret), uri, totp.qrDataUri(uri));
    }

    /** Paso 2: con un código de la app se demuestra que está bien configurada y se activa. */
    @Transactional
    public RecoveryCodes confirmTotp(Long userId, String code) {
        UserMfa mfa = mfaRepository.findById(userId).orElse(null);
        if (mfa == null || mfa.getTotpSecretEnc() == null || mfa.isTotpConfirmed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Primero inicia la configuración de la app autenticadora");
        }
        boolean hadMfa = mfa.anyActive();
        OptionalLong step = totp.verify(cipher.decrypt(mfa.getTotpSecretEnc()), code.trim(), null);
        if (step.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El código no es correcto. Comprueba la hora del móvil e inténtalo de nuevo");
        }
        mfa.setTotpConfirmed(true);
        mfa.setTotpLastStep(step.getAsLong());
        if (mfa.getPreferredMethod() == null) {
            mfa.setPreferredMethod(MfaMethod.TOTP);
        }
        return firstTimeCodes(userId, hadMfa);
    }

    /** Paso 1 del código por correo: envía un código para comprobar que el correo funciona. */
    @Transactional
    public void startEmailEnable(Long userId) {
        User user = findUser(userId);
        if (mfaRepository.findById(userId).map(UserMfa::isEmailEnabled).orElse(false)) {
            throw new ApiException(HttpStatus.CONFLICT, "El código por correo ya está activado");
        }
        emailOtp.send(user);
    }

    @Transactional
    public RecoveryCodes confirmEmail(Long userId, String code) {
        UserMfa mfa = mfaOf(userId);
        boolean hadMfa = mfa.anyActive();
        if (!emailOtp.verify(userId, code.trim())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "El código no es correcto o ha caducado");
        }
        mfa.setEmailEnabled(true);
        if (mfa.getPreferredMethod() == null) {
            mfa.setPreferredMethod(MfaMethod.EMAIL);
        }
        return firstTimeCodes(userId, hadMfa);
    }

    /** Desactiva un método. Pide la contraseña para que una sesión robada no pueda quitar el segundo factor. */
    @Transactional
    public void disable(Long userId, MfaMethod method, String password) {
        requirePassword(findUser(userId), password);
        UserMfa mfa = mfaOf(userId);
        switch (method) {
            case TOTP -> {
                mfa.setTotpSecretEnc(null);
                mfa.setTotpConfirmed(false);
                mfa.setTotpLastStep(null);
            }
            case EMAIL -> mfa.setEmailEnabled(false);
        }
        List<MfaMethod> left = methodsOf(mfa);
        if (left.isEmpty()) {
            mfa.setPreferredMethod(null);
            recovery.deleteAll(userId);
        } else if (!left.contains(mfa.getPreferredMethod())) {
            mfa.setPreferredMethod(left.getFirst());
        }
    }

    @Transactional
    public RecoveryCodes regenerateRecoveryCodes(Long userId, String password) {
        requirePassword(findUser(userId), password);
        if (!isEnabled(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Activa primero un segundo factor");
        }
        return new RecoveryCodes(recovery.regenerate(userId));
    }

    @Transactional
    public void setPreferred(Long userId, MfaMethod method) {
        UserMfa mfa = mfaOf(userId);
        if (!methodsOf(mfa).contains(method)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ese método no está activado");
        }
        mfa.setPreferredMethod(method);
    }

    // ---------- Internos ----------

    private boolean verifyTotp(UserMfa mfa, String code) {
        if (!mfa.totpActive()) {
            return false;
        }
        OptionalLong step = totp.verify(cipher.decrypt(mfa.getTotpSecretEnc()), code.trim(), mfa.getTotpLastStep());
        step.ifPresent(mfa::setTotpLastStep); // el mismo código no se acepta dos veces
        return step.isPresent();
    }

    private RecoveryCodes firstTimeCodes(Long userId, boolean hadMfa) {
        return new RecoveryCodes(hadMfa ? List.of() : recovery.regenerate(userId));
    }

    private static List<MfaMethod> methodsOf(UserMfa mfa) {
        List<MfaMethod> methods = new ArrayList<>(2);
        if (mfa.totpActive()) {
            methods.add(MfaMethod.TOTP);
        }
        if (mfa.isEmailEnabled()) {
            methods.add(MfaMethod.EMAIL);
        }
        return methods;
    }

    private UserMfa mfaOf(Long userId) {
        return mfaRepository.findById(userId).orElseGet(() -> mfaRepository.save(new UserMfa(userId)));
    }

    private User findUser(Long userId) {
        return users.findById(userId).orElseThrow(MfaService::unauthorized);
    }

    private User userFrom(JwtService.MfaTokenData data) {
        return users.findById(data.userId()).filter(u -> u.getStatus() == UserStatus.ACTIVE).orElseThrow(MfaService::unauthorized);
    }

    private void requirePassword(User user, String password) {
        // 403 y no 401: un 401 haría que el frontend intente renovar la sesión en bucle
        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "La contraseña no es correcta");
        }
    }

    private static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Sesión no válida");
    }
}
