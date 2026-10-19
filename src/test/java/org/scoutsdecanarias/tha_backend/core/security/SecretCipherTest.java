package org.scoutsdecanarias.tha_backend.core.security;

import org.junit.jupiter.api.Test;
import org.scoutsdecanarias.tha_backend.features.mfa.MfaProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private static String key(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static SecretCipher cipher(String base64Key) {
        return new SecretCipher(new MfaProperties(base64Key, "Tha", Duration.ofMinutes(10), Duration.ofMinutes(5)));
    }

    private static final String KEY_A = key("0123456789abcdef0123456789abcdef");
    private static final String KEY_B = key("fedcba9876543210fedcba9876543210");

    @Test
    void cifraYDescifra() {
        SecretCipher cipher = cipher(KEY_A);

        String stored = cipher.encrypt("secreto-totp");

        assertThat(stored).doesNotContain("secreto-totp");
        assertThat(cipher.decrypt(stored)).isEqualTo("secreto-totp");
    }

    @Test
    void elMismoTextoDaCifradosDistintosPorqueElIvEsAleatorio() {
        SecretCipher cipher = cipher(KEY_A);

        assertThat(cipher.encrypt("igual")).isNotEqualTo(cipher.encrypt("igual"));
    }

    @Test
    void conOtraClaveNoSePuedeDescifrar() {
        String stored = cipher(KEY_A).encrypt("secreto");

        assertThatThrownBy(() -> cipher(KEY_B).decrypt(stored)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void siSeManipulaElCifradoSeDetecta() {
        SecretCipher cipher = cipher(KEY_A);
        byte[] bytes = Base64.getDecoder().decode(cipher.encrypt("secreto"));
        bytes[bytes.length - 1] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(Base64.getEncoder().encodeToString(bytes)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exigeUnaClaveDe32Bytes() {
        assertThatThrownBy(() -> cipher(key("demasiado-corta"))).isInstanceOf(IllegalStateException.class);
    }
}
