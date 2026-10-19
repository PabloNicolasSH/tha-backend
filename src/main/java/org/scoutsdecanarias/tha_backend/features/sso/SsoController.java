package org.scoutsdecanarias.tha_backend.features.sso;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/auth/sso")
public class SsoController {

    /** Proveedor SSO disponible. {@code loginUrl} es a donde navega el botón "Entrar con …". */
    public record Provider(String id, String name, String loginUrl) {
    }

    private final ObjectProvider<ClientRegistrationRepository> registrations;

    public SsoController(ObjectProvider<ClientRegistrationRepository> registrations) {
        this.registrations = registrations;
    }

    /** Proveedores configurados; vacío si el SSO no está activado. El frontend decide con esto si muestra los botones. */
    @GetMapping("/providers")
    List<Provider> providers() {
        List<Provider> result = new ArrayList<>();
        ClientRegistrationRepository repository = registrations.getIfAvailable();
        if (repository instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item instanceof ClientRegistration r) {
                    result.add(new Provider(r.getRegistrationId(), r.getClientName(),
                            "/oauth2/authorization/" + r.getRegistrationId()));
                }
            }
        }
        return result;
    }
}
