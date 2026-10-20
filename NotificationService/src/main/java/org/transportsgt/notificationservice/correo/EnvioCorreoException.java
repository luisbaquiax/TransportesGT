package org.transportsgt.notificationservice.correo;

import org.springframework.http.HttpStatus;
import org.transportsgt.notificationservice.exception.NotificationServiceException;

/**
 * El servidor SMTP rechazó o no aceptó el correo. El mensaje solo describe el fallo (tipo y respuesta del servidor):
 * nunca incluye el cuerpo, enlaces ni tokens. No sale por HTTP.
 */
public class EnvioCorreoException extends NotificationServiceException {

    public EnvioCorreoException(String motivo, Throwable causa) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "CORREO_NO_ENVIADO", motivo);
        initCause(causa);
    }
}
