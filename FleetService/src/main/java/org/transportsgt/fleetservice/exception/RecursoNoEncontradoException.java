package org.transportsgt.fleetservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 404: el recurso no existe.
 */
public abstract class RecursoNoEncontradoException extends FlotaException {

    protected RecursoNoEncontradoException(String codigo, String mensaje) {
        super(HttpStatus.NOT_FOUND, codigo, mensaje);
    }
}
