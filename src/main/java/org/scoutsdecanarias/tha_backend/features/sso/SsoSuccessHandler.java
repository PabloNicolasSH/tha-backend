package org.scoutsdecanarias.tha_backend.features.sso;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.scoutsdecanarias.tha_backend.features.auth.RefreshCookieFactory;
import org.scoutsdecanarias.tha_backend.features.auth.RefreshTokenService;
import org.scoutsdecanarias.tha_backend.features.user.User;
import org.scoutsdecanarias.tha_backend.features.user.UserRepository;
import org.scoutsdecanarias.tha_backend.features.user.UserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

/**
 * Se ejecuta cuando el proveedor SSO ya ha autenticado a la persona. El SSO solo prueba QUIÉN es: no crea cuentas.
 * Debe existir ya una cuenta ACTIVA aprobada por la federación, que se localiza por la identidad enlazada
 * (proveedor + sub) o, la primera vez, por el email verificado por el proveedor. Entonces se abre la sesión de
 * Tha (refresh token en cookie) y se redirige al frontend, que pide el access token. La sesión HTTP del baile
 * OAuth se descarta: la API sigue siendo sin estado. El proveedor ya ha pedido sus propios factores, así que
 * no se pide el segundo factor de Tha.
 */
@Component
public class SsoSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(SsoSuccessHandler.class);

    private final UserRepository users;
    private final ExternalIdentityRepository identities;
    private final RefreshTokenService refreshTokens;
    private final RefreshCookieFactory cookies;
    private final String frontendUrl;

    public SsoSuccessHandler(UserRepository users, ExternalIdentityRepository identities, RefreshTokenService refreshTokens,
                             RefreshCookieFactory cookies, @Value("${tha.frontend-url}") String frontendUrl) {
        this.users = users;
        this.identities = identities;
        this.refreshTokens = refreshTokens;
        this.cookies = cookies;
        this.frontendUrl = frontendUrl;
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        endOAuthSession(request);

        if (!(authentication instanceof OAuth2AuthenticationToken token) || !(token.getPrincipal() instanceof OidcUser oidc)) {
            redirect(response, "/entrar?sso=error"); // solo se admiten proveedores OpenID Connect (dan email verificado)
            return;
        }
        String provider = token.getAuthorizedClientRegistrationId();
        String subject = oidc.getSubject();
        String email = oidc.getEmail();
        boolean emailVerified = Boolean.TRUE.equals(oidc.getEmailVerified());

        User user = identities.findByProviderAndSubject(provider, subject).map(ExternalIdentity::getUser).orElse(null);
        if (user == null && emailVerified && email != null) {
            // Primera vez: se enlaza por email, pero solo si el proveedor lo ha verificado y la cuenta ya existe y está activa
            user = users.findByEmailIgnoreCase(email).filter(u -> u.getStatus() == UserStatus.ACTIVE).orElse(null);
            if (user != null) {
                ExternalIdentity identity = new ExternalIdentity();
                identity.setUser(user);
                identity.setProvider(provider);
                identity.setSubject(subject);
                identity.setEmail(email);
                identities.save(identity);
                log.info("Identidad SSO enlazada: usuario {} con proveedor {}", user.getId(), provider);
            }
        }
        if (user == null) {
            redirect(response, "/entrar?sso=sin-cuenta");
            return;
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            redirect(response, "/entrar?sso=cuenta-no-activa");
            return;
        }

        String refresh = refreshTokens.startSession(user, false).rawToken();
        response.addHeader(HttpHeaders.SET_COOKIE, cookies.issue(refresh, false));
        redirect(response, "/sso/completar");
    }

    private void endOAuthSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    private void redirect(HttpServletResponse response, String path) throws IOException {
        response.sendRedirect(frontendUrl + path);
    }
}
