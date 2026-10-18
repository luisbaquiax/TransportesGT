package org.transportsgt.fleetservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 403: el usuario no puede realizar la operación.
 */
public abstract class AccesoDenegadoException extends FlotaException {

    protected AccesoDenegadoException(String codigo, String mensaje) {
        super(HttpStatus.FORBIDDEN, codigo, mensaje);
    }
}
