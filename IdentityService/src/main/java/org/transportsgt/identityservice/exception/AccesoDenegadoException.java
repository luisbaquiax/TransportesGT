package org.transportsgt.identityservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 403: el usuario no puede realizar la operación.
 */
public abstract class AccesoDenegadoException extends IdentityServiceException {

    protected AccesoDenegadoException(String codigo, String mensaje) {
        super(HttpStatus.FORBIDDEN, codigo, mensaje);
    }
}
