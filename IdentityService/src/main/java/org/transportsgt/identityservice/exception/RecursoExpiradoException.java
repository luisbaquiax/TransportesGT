package org.transportsgt.identityservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 410: el recurso existió pero ya no está disponible.
 */
public abstract class RecursoExpiradoException extends IdentityServiceException {

    protected RecursoExpiradoException(String codigo, String mensaje) {
        super(HttpStatus.GONE, codigo, mensaje);
    }
}
