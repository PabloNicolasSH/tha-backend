package org.scoutsdecanarias.tha_backend.features.auth;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.scoutsdecanarias.tha_backend.AbstractIntegrationTest;
import org.scoutsdecanarias.tha_backend.features.user.Role;
import org.scoutsdecanarias.tha_backend.features.user.UserStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowTest extends AbstractIntegrationTest {

    // ---------- Solicitud, aprobación y creación de contraseña ----------

    @Test
    void solicitudSinContrasenaAprobacionYEnlaceDeUnSoloUso() throws Exception {
        // 1. La solicitud no lleva contraseña
        register("ana@example.org", "Ana").andExpect(status().isAccepted());
        register("ana@example.org", "Ana").andExpect(status().isConflict());
        assertThat(users.findByEmailIgnoreCase("ana@example.org").orElseThrow().getPasswordHash()).isNull();

        // 2. Pendiente: no puede entrar
        login("ana@example.org", PASSWORD, false).andExpect(status().isForbidden());

        // 3. Solo ADMIN/SECRETARY gestionan solicitudes
        mvc.perform(get("/api/admin/account-requests")).andExpect(status().isUnauthorized());
        activeUser("socio@example.org", Set.of(Role.MEMBER));
        String memberToken = accessToken("socio@example.org");
        mvc.perform(get("/api/admin/account-requests").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());

        // 4. La secretaría aprueba: se envía un enlace al email de la persona
        activeUser("secretaria@example.org", Set.of(Role.SECRETARY));
        String secretary = accessToken("secretaria@example.org");
        Long id = users.findByEmailIgnoreCase("ana@example.org").orElseThrow().getId();
        mvc.perform(post("/api/admin/account-requests/" + id + "/approve").header("Authorization", "Bearer " + secretary))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVITED"));
        String token = tokenSentTo("ana@example.org");

        // 5. Aprobada pero sin contraseña todavía: se le explica qué hacer
        login("ana@example.org", PASSWORD, false).andExpect(status().isForbidden());

        // 6. El enlace es válido; una contraseña corta se rechaza y no gasta el token
        mvc.perform(get("/api/auth/set-password/check").param("token", token)).andExpect(status().isNoContent());
        setPassword(token, "corta").andExpect(status().isBadRequest());
        mvc.perform(get("/api/auth/set-password/check").param("token", token)).andExpect(status().isNoContent());

        // 7. Crea su contraseña: el enlace se gasta
        setPassword(token, PASSWORD).andExpect(status().isNoContent());
        setPassword(token, "otra-clave-larga-9").andExpect(status().isGone());
        mvc.perform(get("/api/auth/set-password/check").param("token", token)).andExpect(status().isGone());

        // 8. Ya puede entrar
        login("ana@example.org", PASSWORD, false).andExpect(status().isOk());
        assertThat(users.findByEmailIgnoreCase("ana@example.org").orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void reenviarInvitacionInvalidaElEnlaceAnteriorYRechazarEliminaLaSolicitud() throws Exception {
        activeUser("admin2@example.org", Set.of(Role.ADMIN));
        String admin = accessToken("admin2@example.org");
        register("luis@example.org", "Luis").andExpect(status().isAccepted());
        Long id = users.findByEmailIgnoreCase("luis@example.org").orElseThrow().getId();

        mvc.perform(post("/api/admin/account-requests/" + id + "/approve").header("Authorization", "Bearer " + admin));
        String first = tokenSentTo("luis@example.org");
        mvc.perform(post("/api/admin/account-requests/" + id + "/resend-invitation").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        String second = lastTokenSentTo("luis@example.org");

        assertThat(second).isNotEqualTo(first);
        setPassword(first, PASSWORD).andExpect(status().isGone());
        mvc.perform(get("/api/auth/set-password/check").param("token", second)).andExpect(status().isNoContent());

        mvc.perform(delete("/api/admin/account-requests/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertThat(users.findByEmailIgnoreCase("luis@example.org")).isEmpty();
        mvc.perform(get("/api/auth/set-password/check").param("token", second)).andExpect(status().isGone());
    }

    // ---------- Mantener sesión iniciada ----------

    @Test
    void mantenerSesionDecideSiLaCookieEsPersistenteYSeConservaAlRenovar() throws Exception {
        activeUser("marta@example.org", Set.of(Role.MEMBER));

        // Sin marcar: cookie de sesión (sin Max-Age)
        MvcResult sessionLogin = login("marta@example.org", PASSWORD, false).andExpect(status().isOk()).andReturn();
        assertThat(sessionLogin.getResponse().getHeader("Set-Cookie"))
                .contains("HttpOnly").contains("SameSite=Strict").doesNotContain("Max-Age");
        MvcResult sessionRefresh = mvc.perform(post("/api/auth/refresh").cookie(sessionLogin.getResponse().getCookie("tha_refresh")))
                .andExpect(status().isOk()).andReturn();
        assertThat(sessionRefresh.getResponse().getHeader("Set-Cookie")).doesNotContain("Max-Age");

        // Marcado: cookie persistente de 30 días, también tras renovar
        MvcResult remembered = login("marta@example.org", PASSWORD, true).andExpect(status().isOk()).andReturn();
        assertThat(remembered.getResponse().getHeader("Set-Cookie")).contains("Max-Age=2592000");
        MvcResult rememberedRefresh = mvc.perform(post("/api/auth/refresh").cookie(remembered.getResponse().getCookie("tha_refresh")))
                .andExpect(status().isOk()).andReturn();
        assertThat(rememberedRefresh.getResponse().getHeader("Set-Cookie")).contains("Max-Age=2592000");
    }

    // ---------- Refresh token: rotación, reutilización y logout ----------

    @Test
    void refreshRotaYLaReutilizacionRevocaLaFamilia() throws Exception {
        activeUser("pepe@example.org", Set.of(Role.MEMBER));
        MvcResult login = login("pepe@example.org", PASSWORD, true).andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty()).andReturn();
        assertThat(login.getResponse().getContentAsString()).doesNotContain("tha_refresh");
        Cookie first = login.getResponse().getCookie("tha_refresh");
        String accessToken = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("pepe@example.org"));
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());

        Cookie second = mvc.perform(post("/api/auth/refresh").cookie(first)).andExpect(status().isOk())
                .andReturn().getResponse().getCookie("tha_refresh");
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Reutilizar un token ya canjeado = posible robo: 401 y toda la familia queda revocada
        mvc.perform(post("/api/auth/refresh").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(second)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevocaLaSesion() throws Exception {
        activeUser("rosa@example.org", Set.of(Role.MEMBER));
        Cookie cookie = login("rosa@example.org", PASSWORD, false).andReturn().getResponse().getCookie("tha_refresh");

        mvc.perform(post("/api/auth/logout").cookie(cookie)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").cookie(cookie)).andExpect(status().isUnauthorized());
    }

    @Test
    void credencialesIncorrectasDan401() throws Exception {
        login("nadie@example.org", "cualquiera-123", false).andExpect(status().isUnauthorized());
    }

    // ---------- Utilidades ----------

    private org.springframework.test.web.servlet.ResultActions register(String email, String name) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"name\":\"" + name + "\",\"surname\":\"Prueba\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions setPassword(String token, String password) throws Exception {
        return mvc.perform(post("/api/auth/set-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"password\":\"" + password + "\"}"));
    }

    /** Token del primer enlace enviado a ese email (lo que recibiría la persona por correo). */
    private String tokenSentTo(String email) {
        return tokenFrom(email, false);
    }

    private String lastTokenSentTo(String email) {
        return tokenFrom(email, true);
    }

    private String tokenFrom(String email, boolean last) {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mail, atLeastOnce()).sendInvitation(eq(email), any(), link.capture(), any(Duration.class));
        String url = last ? link.getAllValues().getLast() : link.getAllValues().getFirst();
        assertThat(url).startsWith("http://localhost:4200/crear-contrasena?token=");
        return url.substring(url.indexOf("token=") + "token=".length());
    }
}
