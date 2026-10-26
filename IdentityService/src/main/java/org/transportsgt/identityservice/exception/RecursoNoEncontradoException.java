package org.transportsgt.identityservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 404: el recurso no existe.
 */
public abstract class RecursoNoEncontradoException extends IdentityServiceException {

    protected RecursoNoEncontradoException(String codigo, String mensaje) {
        super(HttpStatus.NOT_FOUND, codigo, mensaje);
    }
}
