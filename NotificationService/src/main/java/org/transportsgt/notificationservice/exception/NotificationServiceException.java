package org.transportsgt.notificationservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Excepción base del servicio de notificaciones.
 */
@Getter
public class NotificationServiceException extends RuntimeException {
    private final HttpStatus status;

    /**
     * Código estable para el cliente
     */
    private final String codigo;

    protected NotificationServiceException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }
}
