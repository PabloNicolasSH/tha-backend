package org.scoutsdecanarias.tha_backend.core.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** Envío de correos. Es asíncrono: un SMTP lento o caído no debe bloquear ni romper la petición. */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender sender;
    private final String from;

    public MailService(JavaMailSender sender, @Value("${tha.mail.from}") String from) {
        this.sender = sender;
        this.from = from;
    }

    @Async
    public void sendInvitation(String to, String name, String link, Duration validFor) {
        long hours = validFor.toHours();
        send(to, "Tu cuenta de Tha está aprobada",
                "Hola, " + name + ":\n\n"
                        + "La federación ha aprobado tu cuenta de Tha. Crea tu contraseña desde este enlace:\n\n"
                        + link + "\n\n"
                        + "El enlace se puede usar una sola vez y caduca en " + hours + " horas. "
                        + "Si caduca, pide que te lo reenvíen.\n\n"
                        + "Si no has solicitado esta cuenta, ignora este mensaje.\n");
    }

    @Async
    public void sendLoginCode(String to, String name, String code, long minutes) {
        send(to, "Tu código de verificación de Tha",
                "Hola, " + name + ":\n\n"
                        + "Tu código de verificación es: " + code + "\n\n"
                        + "Caduca en " + minutes + " minutos y solo se puede usar una vez. "
                        + "Si no has intentado entrar en Tha, cambia tu contraseña: alguien la conoce.\n");
    }

    private void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
        } catch (RuntimeException e) {
            // Nunca se registra el cuerpo: contiene el enlace con el token.
            log.error("No se pudo enviar el correo '{}' a {}: {}", subject, to, e.getMessage());
        }
    }
}
