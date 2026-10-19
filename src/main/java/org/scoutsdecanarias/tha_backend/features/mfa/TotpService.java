package org.scoutsdecanarias.tha_backend.features.mfa;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.OptionalLong;
import java.util.regex.Pattern;

/** TOTP (RFC 6238): códigos de 6 dígitos cada 30 s, compatibles con Google/Microsoft Authenticator, Aegis, 1Password… */
@Service
public class TotpService {

    private static final Duration STEP = Duration.ofSeconds(30);
    private static final Pattern SIX_DIGITS = Pattern.compile("\\d{6}");
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final TimeBasedOneTimePasswordGenerator generator = new TimeBasedOneTimePasswordGenerator(STEP, 6);
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final String issuer;

    @Autowired
    public TotpService(MfaProperties properties) {
        this(properties.issuer(), Clock.systemUTC());
    }

    /** Constructor con reloj propio, para probar ventanas de tiempo y reutilización. */
    TotpService(String issuer, Clock clock) {
        this.issuer = issuer;
        this.clock = clock;
    }

    /** Secreto nuevo de 160 bits, en Base64 para guardarlo (cifrado). */
    public String newSecret() {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** El secreto en Base32, que es lo que se teclea a mano en la app autenticadora. */
    public String base32(String secret) {
        byte[] data = Base64.getDecoder().decode(secret);
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    /** URI que leen las apps autenticadoras al escanear el QR. */
    public String otpauthUri(String secret, String accountEmail) {
        String label = encode(issuer) + ":" + encode(accountEmail);
        return "otpauth://totp/" + label + "?secret=" + base32(secret)
                + "&issuer=" + encode(issuer) + "&algorithm=SHA1&digits=6&period=30";
    }

    /**
     * Comprueba un código admitiendo un intervalo de margen por cada lado (desfase del reloj del móvil).
     * Devuelve el intervalo aceptado, que debe guardarse para no aceptar el mismo código dos veces.
     * Los intervalos iguales o anteriores a {@code lastUsedStep} se rechazan.
     */
    public OptionalLong verify(String secret, String code, Long lastUsedStep) {
        if (code == null || !SIX_DIGITS.matcher(code).matches()) {
            return OptionalLong.empty();
        }
        SecretKeySpec key = new SecretKeySpec(Base64.getDecoder().decode(secret), generator.getAlgorithm());
        long currentStep = clock.instant().getEpochSecond() / STEP.toSeconds();
        for (long step = currentStep - 1; step <= currentStep + 1; step++) {
            if (lastUsedStep != null && step <= lastUsedStep) {
                continue;
            }
            String expected = codeAt(key, step);
            if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), code.getBytes(StandardCharsets.UTF_8))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    /** Código del intervalo actual desplazado {@code offset} intervalos (lo usan los tests para simular la app del usuario). */
    String codeAtOffset(String secret, int offset) {
        SecretKeySpec key = new SecretKeySpec(Base64.getDecoder().decode(secret), generator.getAlgorithm());
        return codeAt(key, clock.instant().getEpochSecond() / STEP.toSeconds() + offset);
    }

    /** QR de la URI como imagen SVG en data-URI (se muestra con un simple &lt;img&gt;). El secreto no sale a ningún servicio externo. */
    public String qrDataUri(String otpauthUri) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(otpauthUri, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.MARGIN, 2));
            StringBuilder svg = new StringBuilder();
            svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(matrix.getWidth()).append(' ')
                    .append(matrix.getHeight()).append("\" shape-rendering=\"crispEdges\">")
                    .append("<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path fill=\"#000\" d=\"");
            for (int y = 0; y < matrix.getHeight(); y++) {
                int x = 0;
                while (x < matrix.getWidth()) {
                    if (!matrix.get(x, y)) {
                        x++;
                        continue;
                    }
                    int start = x;
                    while (x < matrix.getWidth() && matrix.get(x, y)) {
                        x++;
                    }
                    svg.append('M').append(start).append(' ').append(y).append('h').append(x - start).append("v1h-").append(x - start).append('z');
                }
            }
            svg.append("\"/></svg>");
            return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.toString().getBytes(StandardCharsets.UTF_8));
        } catch (WriterException e) {
            throw new IllegalStateException("No se pudo generar el QR", e);
        }
    }

    private String codeAt(SecretKeySpec key, long step) {
        try {
            return generator.generateOneTimePasswordString(key, Instant.ofEpochSecond(step * STEP.toSeconds()));
        } catch (InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
