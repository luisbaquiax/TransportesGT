package org.transportsgt.notificationservice.exception;

import org.springframework.http.HttpStatus;

public abstract class RecursoNoEncontradoException extends NotificationServiceException {

    protected RecursoNoEncontradoException(String codigo, String mensaje) {
        super(HttpStatus.NOT_FOUND, codigo, mensaje);
    }
}
