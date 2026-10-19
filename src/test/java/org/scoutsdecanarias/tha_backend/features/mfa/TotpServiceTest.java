package org.scoutsdecanarias.tha_backend.features.mfa;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    // Secreto de prueba del RFC 6238 (apéndice B): los 20 bytes ASCII "12345678901234567890"
    private static final String RFC_SECRET =
            Base64.getEncoder().encodeToString("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    private static TotpService at(long epochSeconds) {
        return new TotpService("Tha", Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC));
    }

    @Test
    void coincideConElVectorOficialDelRfc6238() {
        // Con 6 dígitos, el RFC da 287082 en t=59 y 081804 en t=1111111109
        assertThat(at(59).codeAtOffset(RFC_SECRET, 0)).isEqualTo("287082");
        assertThat(at(1111111109L).codeAtOffset(RFC_SECRET, 0)).isEqualTo("081804");
    }

    @Test
    void base32EsElDelRfc4648() {
        assertThat(at(0).base32(RFC_SECRET)).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
    }

    @Test
    void admiteUnIntervaloDeMargenPorCadaLadoPeroNoMas() {
        TotpService service = at(1_000_000_000L);

        assertThat(service.verify(RFC_SECRET, service.codeAtOffset(RFC_SECRET, 0), null)).isPresent();
        assertThat(service.verify(RFC_SECRET, service.codeAtOffset(RFC_SECRET, -1), null)).isPresent(); // móvil adelantado
        assertThat(service.verify(RFC_SECRET, service.codeAtOffset(RFC_SECRET, 1), null)).isPresent();  // móvil atrasado
        assertThat(service.verify(RFC_SECRET, service.codeAtOffset(RFC_SECRET, 2), null)).isEmpty();
        assertThat(service.verify(RFC_SECRET, service.codeAtOffset(RFC_SECRET, -2), null)).isEmpty();
    }

    @Test
    void unCodigoYaUsadoNoSeAceptaDeNuevo() {
        TotpService service = at(1_000_000_000L);
        String code = service.codeAtOffset(RFC_SECRET, 0);

        OptionalLong step = service.verify(RFC_SECRET, code, null);
        assertThat(step).isPresent();
        assertThat(service.verify(RFC_SECRET, code, step.getAsLong())).isEmpty();
    }

    @Test
    void rechazaCodigosMalFormados() {
        TotpService service = at(59);

        assertThat(service.verify(RFC_SECRET, null, null)).isEmpty();
        assertThat(service.verify(RFC_SECRET, "28708", null)).isEmpty();
        assertThat(service.verify(RFC_SECRET, "2870821", null)).isEmpty();
        assertThat(service.verify(RFC_SECRET, "abcdef", null)).isEmpty();
    }

    @Test
    void laUriDelQrTieneElFormatoQueEntiendenLasApps() {
        TotpService service = at(0);

        assertThat(service.otpauthUri(RFC_SECRET, "ana+tha@example.org"))
                .isEqualTo("otpauth://totp/Tha:ana%2Btha%40example.org?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
                        + "&issuer=Tha&algorithm=SHA1&digits=6&period=30");
        String qr = service.qrDataUri(service.otpauthUri(RFC_SECRET, "ana@example.org"));
        assertThat(qr).startsWith("data:image/svg+xml;base64,");
        assertThat(new String(Base64.getDecoder().decode(qr.substring(qr.indexOf(',') + 1)), StandardCharsets.UTF_8))
                .startsWith("<svg").contains("viewBox");
    }

    @Test
    void cadaSecretoNuevoEsDistintoYDe160Bits() {
        TotpService service = at(0);
        String a = service.newSecret();
        String b = service.newSecret();

        assertThat(a).isNotEqualTo(b);
        assertThat(Base64.getDecoder().decode(a)).hasSize(20);
    }
}
