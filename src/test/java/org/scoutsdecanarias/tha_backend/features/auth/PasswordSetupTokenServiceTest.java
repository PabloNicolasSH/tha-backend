package org.scoutsdecanarias.tha_backend.features.auth;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordSetupTokenServiceTest {

    private final AtomicLong nanos = new AtomicLong();
    private final Ticker ticker = nanos::get;
    private final PasswordSetupTokenService service = new PasswordSetupTokenService(Duration.ofHours(48), ticker);

    private void advance(Duration d) {
        nanos.addAndGet(d.toNanos());
    }

    @Test
    void elTokenVale_hastaQueCaduca() {
        String token = service.issueFor(7L);

        advance(Duration.ofHours(47));
        assertThat(service.peek(token)).contains(7L);

        advance(Duration.ofHours(2));
        assertThat(service.peek(token)).isEmpty();
        assertThat(service.consume(token)).isEmpty();
    }

    @Test
    void sePuedeUsarUnaSolaVez() {
        String token = service.issueFor(7L);

        assertThat(service.consume(token)).contains(7L);
        assertThat(service.consume(token)).isEmpty();
        assertThat(service.peek(token)).isEmpty();
    }

    @Test
    void emitirOtroTokenInvalidaElAnterior() {
        String first = service.issueFor(7L);
        String second = service.issueFor(7L);

        assertThat(second).isNotEqualTo(first);
        assertThat(service.peek(first)).isEmpty();
        assertThat(service.peek(second)).contains(7L);
    }

    @Test
    void tokensDeOtrasPersonasNoSeMezclan() {
        String a = service.issueFor(1L);
        String b = service.issueFor(2L);

        assertThat(service.consume(b)).contains(2L);
        assertThat(service.peek(a)).contains(1L);
    }

    @Test
    void tokenInventadoONuloNoSirve() {
        assertThat(service.peek("inventado")).isEmpty();
        assertThat(service.consume(null)).isEmpty();
    }
}
