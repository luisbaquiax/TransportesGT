package org.transportsgt.notificationservice.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Error TRANSITORIO del consumidor: el evento llegó antes que el UsuarioCreado de su destinatario. No es
 * {@code EventoNoRecuperableException}, así que RabbitMQ lo reintenta y, si al agotar los reintentos el contacto
 * sigue sin existir, el mensaje va a la DLQ. No sale por HTTP.
 */
public class ContactoNoDisponibleException extends NotificationServiceException {

    public ContactoNoDisponibleException(UUID idUsuario) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "CONTACTO_NO_DISPONIBLE",
                "Aún no hay contacto para el usuario " + idUsuario);
    }
}
