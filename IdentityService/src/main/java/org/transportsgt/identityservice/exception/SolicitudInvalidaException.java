package org.transportsgt.identityservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 400: la solicitud no es válida.
 */
public abstract class SolicitudInvalidaException extends IdentityServiceException {

    protected SolicitudInvalidaException(String codigo, String mensaje) {
        super(HttpStatus.BAD_REQUEST, codigo, mensaje);
    }
}
