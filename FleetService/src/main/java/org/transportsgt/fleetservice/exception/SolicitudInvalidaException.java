package org.transportsgt.fleetservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 400: la solicitud no es válida.
 */
public abstract class SolicitudInvalidaException extends FlotaException {

    protected SolicitudInvalidaException(String codigo, String mensaje) {
        super(HttpStatus.BAD_REQUEST, codigo, mensaje);
    }
}
