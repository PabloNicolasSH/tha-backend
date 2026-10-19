package org.scoutsdecanarias.tha_backend.features.sso;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cadena aparte para el baile OAuth2/OIDC ({@code /oauth2/**} y {@code /login/oauth2/**}). Usa una sesión HTTP
 * solo mientras dura el baile (guarda el "state" anti-CSRF) y la descarta al terminar. El resto de la API
 * sigue en la cadena sin sesión de {@code SecurityConfig}.
 *
 * Si no hay ningún proveedor configurado (perfil "sso" desactivado) estas rutas quedan cerradas.
 */
@Configuration
class SsoSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain ssoFilterChain(HttpSecurity http, ObjectProvider<ClientRegistrationRepository> registrations,
                                       SsoSuccessHandler successHandler, @Value("${tha.frontend-url}") String frontendUrl)
            throws Exception {
        http.securityMatcher("/oauth2/**", "/login/oauth2/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        if (registrations.getIfAvailable() == null) {
            http.authorizeHttpRequests(a -> a.anyRequest().denyAll());
        } else {
            http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
                    .oauth2Login(o -> o
                            .successHandler(successHandler)
                            .failureHandler((request, response, exception) -> response.sendRedirect(frontendUrl + "/entrar?sso=error")));
        }
        return http.build();
    }
}
