package org.scoutsdecanarias.tha_backend.features.sso;

import org.junit.jupiter.api.Test;
import org.scoutsdecanarias.tha_backend.AbstractIntegrationTest;
import org.scoutsdecanarias.tha_backend.features.user.Role;
import org.scoutsdecanarias.tha_backend.features.user.User;
import org.scoutsdecanarias.tha_backend.features.user.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba la lógica de Tha tras el baile con el proveedor (qué cuenta se abre y a dónde se redirige) simulando
 * lo que Spring Security entrega al terminar. El baile con un proveedor real (Google, Entra, Keycloak…) solo
 * se puede comprobar contra ese proveedor.
 */
class SsoSuccessHandlerTest extends AbstractIntegrationTest {

    @Autowired SsoSuccessHandler handler;
    @Autowired ExternalIdentityRepository identities;

    private static OAuth2AuthenticationToken oidc(String subject, String email, boolean emailVerified) {
        OidcIdToken idToken = new OidcIdToken("token", Instant.now(), Instant.now().plusSeconds(300),
                Map.of("sub", subject, "email", email, "email_verified", emailVerified));
        DefaultOidcUser user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("OIDC_USER")), idToken);
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "sso");
    }

    private MockHttpServletResponse run(OAuth2AuthenticationToken token, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.onAuthenticationSuccess(request, response, token);
        return response;
    }

    private MockHttpServletResponse run(OAuth2AuthenticationToken token) throws Exception {
        return run(token, new MockHttpServletRequest());
    }

    @Test
    void cuentaActivaConEmailVerificadoSeEnlazaYAbreSesion() throws Exception {
        User ana = activeUser("sso-ana@example.org", Set.of(Role.MEMBER));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());

        MockHttpServletResponse response = run(oidc("google-sub-1", "SSO-Ana@example.org", true), request);

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/sso/completar");
        // Cookie de refresh: HttpOnly, SameSite=Strict y de sesión (sin Max-Age)
        assertThat(response.getHeader("Set-Cookie")).startsWith("tha_refresh=").contains("HttpOnly").contains("SameSite=Strict")
                .doesNotContain("Max-Age");
        assertThat(identities.findByProviderAndSubject("sso", "google-sub-1").orElseThrow().getUser().getId()).isEqualTo(ana.getId());
        // La sesión HTTP del baile OAuth no se conserva: la API sigue sin estado
        assertThat(request.getSession(false)).isNull();
    }

    @Test
    void laCookieQueDaElSsoPermiteObtenerUnAccessToken() throws Exception {
        activeUser("sso-cookie@example.org", Set.of(Role.MEMBER));
        MockHttpServletResponse response = run(oidc("sub-cookie", "sso-cookie@example.org", true));
        String setCookie = response.getHeader("Set-Cookie");
        String value = setCookie.substring("tha_refresh=".length(), setCookie.indexOf(';'));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("tha_refresh", value)))
                .andExpect(status().isOk());
    }

    @Test
    void conIdentidadYaEnlazadaEntraAunqueCambieElEmail() throws Exception {
        activeUser("sso-luis@example.org", Set.of(Role.MEMBER));
        run(oidc("sub-luis", "sso-luis@example.org", true)); // primer acceso: se enlaza

        MockHttpServletResponse second = run(oidc("sub-luis", "otro-correo@example.org", false));

        assertThat(second.getRedirectedUrl()).isEqualTo("http://localhost:4200/sso/completar");
    }

    @Test
    void sinCuentaNoSeCreaNada() throws Exception {
        MockHttpServletResponse response = run(oidc("sub-nadie", "nadie-sso@example.org", true));

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/entrar?sso=sin-cuenta");
        assertThat(response.getHeader("Set-Cookie")).isNull();
        assertThat(users.findByEmailIgnoreCase("nadie-sso@example.org")).isEmpty();
    }

    @Test
    void conEmailSinVerificarNoSeEnlazaPorEmail() throws Exception {
        activeUser("sso-victima@example.org", Set.of(Role.MEMBER));

        // Un atacante con un proveedor que no verifica correos podría declarar el email de otra persona
        MockHttpServletResponse response = run(oidc("sub-atacante", "sso-victima@example.org", false));

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/entrar?sso=sin-cuenta");
        assertThat(response.getHeader("Set-Cookie")).isNull();
        assertThat(identities.findByProviderAndSubject("sso", "sub-atacante")).isEmpty();
    }

    @Test
    void cuentaDesactivadaONoActivaNoEntra() throws Exception {
        User off = activeUser("sso-off@example.org", Set.of(Role.MEMBER));
        run(oidc("sub-off", "sso-off@example.org", true)); // enlazada mientras estaba activa
        off.setStatus(UserStatus.DISABLED);
        users.save(off);

        MockHttpServletResponse response = run(oidc("sub-off", "sso-off@example.org", true));

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/entrar?sso=cuenta-no-activa");
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void invitadaSinContrasenaNoSeEnlazaPorEmail() throws Exception {
        User invited = activeUser("sso-invitada@example.org", Set.of(Role.MEMBER));
        invited.setStatus(UserStatus.INVITED);
        invited.setPasswordHash(null);
        users.save(invited);

        MockHttpServletResponse response = run(oidc("sub-inv", "sso-invitada@example.org", true));

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/entrar?sso=sin-cuenta");
    }

    @Test
    void soloSeAceptanProveedoresOpenIdConnect() throws Exception {
        activeUser("sso-oauth@example.org", Set.of(Role.MEMBER));
        DefaultOAuth2User plain = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("USER")),
                Map.of("id", "1", "email", "sso-oauth@example.org"), "id");

        MockHttpServletResponse response = run(new OAuth2AuthenticationToken(plain, plain.getAuthorities(), "sso"));

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:4200/entrar?sso=error");
    }

    @Test
    void sinProveedorConfiguradoElSsoEstaCerradoYNoHayProveedores() throws Exception {
        mvc.perform(get("/api/auth/sso/providers")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().json("[]"));
        mvc.perform(get("/oauth2/authorization/sso")).andExpect(status().isForbidden());
    }
}
