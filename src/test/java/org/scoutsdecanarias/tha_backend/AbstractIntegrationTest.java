package org.scoutsdecanarias.tha_backend;

import com.jayway.jsonpath.JsonPath;
import org.scoutsdecanarias.tha_backend.core.mail.MailService;
import org.scoutsdecanarias.tha_backend.features.user.Role;
import org.scoutsdecanarias.tha_backend.features.user.User;
import org.scoutsdecanarias.tha_backend.features.user.UserRepository;
import org.scoutsdecanarias.tha_backend.features.user.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.mysql.MySQLContainer;

import java.util.EnumSet;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de los tests de integración: un único MySQL real (Testcontainers) para toda la ejecución, de modo que
 * Spring reutilice el contexto entre clases, y utilidades para crear usuarios e iniciar sesión.
 */
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "tha.jwt.secret=test-secret-test-secret-test-secret-0123456789",
        "tha.jwt.issuer=tha",
        "tha.jwt.expiration=PT15M",
        "tha.refresh.expiration=P30D",
        "tha.refresh.session-expiration=PT12H",
        "tha.refresh.cookie-secure=false",
        "tha.invite.expiration=PT48H",
        "tha.frontend-url=http://localhost:4200",
        "tha.mail.from=no-reply@tha.test",
        "tha.cors.allowed-origin=http://localhost:4200",
        "tha.mfa.encryption-key=ZGV2LW9ubHktbWZhLWtleS0zMi1ieXRlcy1sb25nISE=",
        "tha.mfa.issuer=Tha",
        "tha.mfa.email-code-ttl=PT10M",
        "tha.mfa.token-ttl=PT5M"
})
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    static {
        MYSQL.start(); // se para solo al terminar la JVM (Ryuk)
    }

    protected static final String PASSWORD = "una-clave-larga-1";

    @Autowired protected MockMvc mvc;
    @Autowired protected UserRepository users;
    @Autowired protected PasswordEncoder encoder;
    @MockitoBean protected MailService mail;

    protected User activeUser(String email, Set<Role> roles) {
        User user = new User();
        user.setEmail(email);
        user.setName("Test");
        user.setSurname("Usuario");
        user.setPasswordHash(encoder.encode(PASSWORD));
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(EnumSet.copyOf(roles));
        return users.save(user);
    }

    protected ResultActions login(String email, String password, boolean remember) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"rememberMe\":" + remember + "}"));
    }

    /** Inicia sesión (sin segundo factor) y devuelve el access token. */
    protected String accessToken(String email) throws Exception {
        MvcResult result = login(email, PASSWORD, false).andExpect(status().isOk()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }
}
