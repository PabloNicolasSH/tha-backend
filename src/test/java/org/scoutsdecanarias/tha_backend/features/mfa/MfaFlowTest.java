package org.scoutsdecanarias.tha_backend.features.mfa;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.scoutsdecanarias.tha_backend.AbstractIntegrationTest;
import org.scoutsdecanarias.tha_backend.core.security.SecretCipher;
import org.scoutsdecanarias.tha_backend.features.user.Role;
import org.scoutsdecanarias.tha_backend.features.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MfaFlowTest extends AbstractIntegrationTest {

    @Autowired TotpService totp;
    @Autowired UserMfaRepository mfaRepository;
    @Autowired SecretCipher cipher;
    @Autowired MfaAttemptLimiter limiter;

    // ---------- App autenticadora (TOTP) ----------

    @Test
    void altaDeAppAutenticadoraLoginConDosPasosYCodigoNoReutilizable() throws Exception {
        User user = activeUser("totp@example.org", Set.of(Role.MEMBER));
        String access = accessToken("totp@example.org");

        // Alta: el servidor da el QR y el secreto, pero hasta confirmar con un código no se activa
        MvcResult setup = mvc.perform(post("/api/auth/mfa/totp/setup").header("Authorization", bearer(access)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andExpect(jsonPath("$.otpauthUri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/Tha:totp%40example.org?secret=")))
                .andExpect(jsonPath("$.qrDataUri").value(org.hamcrest.Matchers.startsWith("data:image/svg+xml;base64,")))
                .andReturn();
        String base32 = JsonPath.read(setup.getResponse().getContentAsString(), "$.secret");
        assertThat(isEnabled(user)).isFalse();
        login("totp@example.org", PASSWORD, false).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());

        // El secreto se guarda cifrado: el valor en claro no aparece en la base de datos
        UserMfa stored = mfaRepository.findById(user.getId()).orElseThrow();
        assertThat(stored.getTotpSecretEnc()).doesNotContain(base32);
        String secret = cipher.decrypt(stored.getTotpSecretEnc());

        // Confirmar con un código incorrecto no activa nada; con el correcto, sí, y entrega los códigos de recuperación
        confirmTotp(access, "000000").andExpect(status().isBadRequest());
        MvcResult confirmed = confirmTotp(access, totp.codeAtOffset(secret, 0)).andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(10)).andReturn();
        assertThat(JsonPath.<List<String>>read(confirmed.getResponse().getContentAsString(), "$.recoveryCodes").getFirst())
                .matches("[A-Z2-9]{5}-[A-Z2-9]{5}");
        mvc.perform(get("/api/auth/mfa").header("Authorization", bearer(access)))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.methods[0]").value("TOTP"))
                .andExpect(jsonPath("$.recoveryCodesLeft").value(10));

        // Login: la contraseña ya no basta. No hay cookie de sesión, solo un desafío
        MvcResult challenge = login("totp@example.org", PASSWORD, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.methods[0]").value("TOTP"))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn();
        assertThat(challenge.getResponse().getHeader("Set-Cookie")).isNull();
        String mfaToken = JsonPath.read(challenge.getResponse().getContentAsString(), "$.mfaToken");

        // El token temporal NO sirve como access token
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(mfaToken))).andExpect(status().isUnauthorized());

        // Código incorrecto: 401. Correcto: sesión completa (y respeta "mantener sesión")
        submitCode(mfaToken, "TOTP", "123456").andExpect(status().isUnauthorized());
        String code = totp.codeAtOffset(secret, 1); // el intervalo siguiente: el actual ya se gastó al confirmar
        MvcResult done = submitCode(mfaToken, "TOTP", code).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty()).andReturn();
        assertThat(done.getResponse().getHeader("Set-Cookie")).contains("HttpOnly").contains("Max-Age=2592000");

        // El mismo código no vale dos veces
        MvcResult again = login("totp@example.org", PASSWORD, false).andReturn();
        String mfaToken2 = JsonPath.read(again.getResponse().getContentAsString(), "$.mfaToken");
        submitCode(mfaToken2, "TOTP", code).andExpect(status().isUnauthorized());
    }

    // ---------- Códigos de recuperación ----------

    @Test
    void codigoDeRecuperacionEntraUnaSolaVez() throws Exception {
        activeUser("recup@example.org", Set.of(Role.MEMBER));
        String access = accessToken("recup@example.org");
        String secret = startAndConfirmTotp(access, "recup@example.org");
        List<String> codes = recoveryCodesAfterConfirm;

        String mfaToken = challengeToken("recup@example.org");
        // Se acepta en minúsculas y sin guion: es lo que escribiría una persona
        String typed = codes.getFirst().toLowerCase().replace("-", "");
        submitCode(mfaToken, "RECOVERY", typed).andExpect(status().isOk());

        String mfaToken2 = challengeToken("recup@example.org");
        submitCode(mfaToken2, "RECOVERY", codes.getFirst()).andExpect(status().isUnauthorized());
        submitCode(mfaToken2, "RECOVERY", codes.get(1)).andExpect(status().isOk());

        // Cada código gastado deja de contar
        mvc.perform(get("/api/auth/mfa").header("Authorization", bearer(access)))
                .andExpect(jsonPath("$.recoveryCodesLeft").value(8));
        assertThat(secret).isNotBlank();
    }

    // ---------- Código por correo ----------

    @Test
    void codigoPorCorreoSeActivaConConfirmacionYSeEnviaAlHacerLogin() throws Exception {
        User user = activeUser("email2fa@example.org", Set.of(Role.MEMBER));
        String access = accessToken("email2fa@example.org");

        // Activar: se envía un código de comprobación al correo
        mvc.perform(post("/api/auth/mfa/email/enable").header("Authorization", bearer(access))).andExpect(status().isNoContent());
        String enableCode = lastEmailCode("email2fa@example.org");
        confirmEmail(access, "000000").andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/mfa/email/confirm").header("Authorization", bearer(access)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"" + enableCode + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recoveryCodes.length()").value(10));

        // Login: al ser el método preferido, el código sale solo por correo
        MvcResult challenge = login("email2fa@example.org", PASSWORD, false).andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.preferredMethod").value("EMAIL")).andReturn();
        String mfaToken = JsonPath.read(challenge.getResponse().getContentAsString(), "$.mfaToken");
        String loginCode = lastEmailCode("email2fa@example.org");
        assertThat(loginCode).matches("\\d{6}");

        submitCode(mfaToken, "EMAIL", "000000").andExpect(status().isUnauthorized());
        submitCode(mfaToken, "EMAIL", loginCode).andExpect(status().isOk());
        submitCode(mfaToken, "EMAIL", loginCode).andExpect(status().isUnauthorized()); // de un solo uso
        assertThat(isEnabled(user)).isTrue();
    }

    // ---------- Fuerza bruta ----------

    @Test
    void tras5FallosSeBloqueaAunqueElCodigoSeaCorrecto() throws Exception {
        User user = activeUser("bruto@example.org", Set.of(Role.MEMBER));
        String access = accessToken("bruto@example.org");
        String secret = startAndConfirmTotp(access, "bruto@example.org");
        String mfaToken = challengeToken("bruto@example.org");

        for (int i = 0; i < 5; i++) {
            submitCode(mfaToken, "TOTP", "111111").andExpect(status().isUnauthorized());
        }
        submitCode(mfaToken, "TOTP", totp.codeAtOffset(secret, 1)).andExpect(status().isTooManyRequests());
        limiter.reset(user.getId()); // el bloqueo caduca solo a los 15 minutos
    }

    // ---------- Desactivar ----------

    @Test
    void desactivarPideLaContrasenaYElLoginVuelveAUnSoloPaso() throws Exception {
        activeUser("off@example.org", Set.of(Role.MEMBER));
        String access = accessToken("off@example.org");
        startAndConfirmTotp(access, "off@example.org");

        mvc.perform(post("/api/auth/mfa/TOTP/disable").header("Authorization", bearer(access)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"incorrecta-123456\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/mfa/TOTP/disable").header("Authorization", bearer(access)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isNoContent());

        login("off@example.org", PASSWORD, false).andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        mvc.perform(get("/api/auth/mfa").header("Authorization", bearer(access)))
                .andExpect(jsonPath("$.enabled").value(false)).andExpect(jsonPath("$.recoveryCodesLeft").value(0));
    }

    @Test
    void sinSesionNoSePuedeGestionarElSegundoFactor() throws Exception {
        mvc.perform(get("/api/auth/mfa")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/mfa/totp/setup")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/auth/mfa/preferred").contentType(MediaType.APPLICATION_JSON).content("{\"method\":\"TOTP\"}"))
                .andExpect(status().isUnauthorized());
        submitCode("no-es-un-token", "TOTP", "123456").andExpect(status().isUnauthorized());
    }

    // ---------- Utilidades ----------

    private List<String> recoveryCodesAfterConfirm;

    private boolean isEnabled(User user) {
        return mfaRepository.findById(user.getId()).map(UserMfa::anyActive).orElse(false);
    }

    private ResultActions confirmTotp(String access, String code) throws Exception {
        return mvc.perform(post("/api/auth/mfa/totp/confirm").header("Authorization", bearer(access))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
    }

    private ResultActions confirmEmail(String access, String code) throws Exception {
        return mvc.perform(post("/api/auth/mfa/email/confirm").header("Authorization", bearer(access))
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
    }

    private ResultActions submitCode(String mfaToken, String type, String code) throws Exception {
        return mvc.perform(post("/api/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mfaToken\":\"" + mfaToken + "\",\"type\":\"" + type + "\",\"code\":\"" + code + "\"}"));
    }

    /** Configura la app autenticadora de punta a punta y devuelve el secreto (Base64). Guarda los códigos de recuperación. */
    private String startAndConfirmTotp(String access, String email) throws Exception {
        mvc.perform(post("/api/auth/mfa/totp/setup").header("Authorization", bearer(access))).andExpect(status().isOk());
        User user = users.findByEmailIgnoreCase(email).orElseThrow();
        String secret = cipher.decrypt(mfaRepository.findById(user.getId()).orElseThrow().getTotpSecretEnc());
        MvcResult result = confirmTotp(access, totp.codeAtOffset(secret, 0)).andExpect(status().isOk()).andReturn();
        recoveryCodesAfterConfirm = JsonPath.read(result.getResponse().getContentAsString(), "$.recoveryCodes");
        return secret;
    }

    private String challengeToken(String email) throws Exception {
        MvcResult result = login(email, PASSWORD, false).andExpect(jsonPath("$.mfaRequired").value(true)).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.mfaToken");
    }

    /** Último código de 6 dígitos que se envió por correo a esa dirección. */
    private String lastEmailCode(String email) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).sendLoginCode(eq(email), any(), code.capture(), anyLong());
        return code.getAllValues().getLast();
    }

}
