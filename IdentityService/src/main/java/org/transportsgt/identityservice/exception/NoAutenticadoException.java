package org.transportsgt.identityservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 401: no hay credenciales o no son válidas.
 */
public abstract class NoAutenticadoException extends IdentityServiceException {

    protected NoAutenticadoException(String codigo, String mensaje) {
        super(HttpStatus.UNAUTHORIZED, codigo, mensaje);
    }
}
