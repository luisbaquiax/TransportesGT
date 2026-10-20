package org.transportsgt.notificationservice.correo;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.transportsgt.notificationservice.config.NotificacionesProperties;

import java.nio.charset.StandardCharsets;

/**
 * Adaptador SMTP de {@link EnviadorCorreo}: HTML con texto plano alternativo (multipart/alternative).
 */
@Component
@RequiredArgsConstructor
public class SmtpEnviadorCorreo implements EnviadorCorreo {

    private final JavaMailSender mailSender;
    private final TextoPlano textoPlano;
    private final NotificacionesProperties propiedades;

    @Override
    public void enviar(MensajeCorreo mensaje) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(propiedades.remitente());
            helper.setTo(mensaje.destino());
            helper.setSubject(mensaje.asunto());
            helper.setText(textoPlano.desdeHtml(mensaje.html()), mensaje.html());
            mailSender.send(mime);
        } catch (MailException | MessagingException e) {
            throw new EnvioCorreoException(motivo(e), e);
        }
    }

    /**
     * Tipo y respuesta del servidor de la causa más profunda (p. ej. {@code ConnectException: Connection refused}).
     * Ni el cuerpo ni los enlaces llegan a una excepción de JavaMail.
     */
    private static String motivo(Exception e) {
        Throwable raiz = e;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        String detalle = raiz.getMessage() == null ? "" : ": " + raiz.getMessage().strip();
        return raiz.getClass().getSimpleName() + detalle;
    }
}
