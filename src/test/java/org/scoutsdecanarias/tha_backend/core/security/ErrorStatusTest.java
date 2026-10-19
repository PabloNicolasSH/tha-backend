package org.scoutsdecanarias.tha_backend.core.security;

import org.junit.jupiter.api.Test;
import org.scoutsdecanarias.tha_backend.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los errores de la petición deben llegar como lo que son (400, 415…) y no disfrazados de 401.
 * Se prueba con un servidor HTTP real: MockMvc no reenvía a /error, que es donde estaba el fallo.
 */
class ErrorStatusTest extends AbstractIntegrationTest {

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private int post(String path, String contentType, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private int get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void unJsonRotoEnUnEndpointPublicoEsUn400() throws Exception {
        assertThat(post("/api/auth/register", "application/json", "{roto")).isEqualTo(400);
    }

    @Test
    void unTipoDeContenidoNoAdmitidoEsUn415() throws Exception {
        assertThat(post("/api/auth/register", "text/plain", "x")).isEqualTo(415);
    }

    @Test
    void laValidacionSigueDandoUn400() throws Exception {
        assertThat(post("/api/auth/register", "application/json", "{\"email\":\"\",\"name\":\"\",\"surname\":\"\"}")).isEqualTo(400);
    }

    @Test
    void sinSesionLosEndpointsProtegidosSiguenDandoUn401() throws Exception {
        assertThat(get("/api/auth/me")).isEqualTo(401);
        assertThat(get("/api/admin/account-requests")).isEqualTo(401);
        assertThat(post("/api/auth/mfa/totp/setup", "application/json", "{}")).isEqualTo(401);
    }

    @Test
    void unEndpointProtegidoConJsonRotoNoSeSaltaLaAutenticacion() throws Exception {
        assertThat(post("/api/auth/mfa/totp/confirm", "application/json", "{roto")).isEqualTo(401);
    }
}
