package org.transportsgt.fleetservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 409: choca con el estado actual de otro registro.
 */
public abstract class ConflictoException extends FlotaException {

    protected ConflictoException(String codigo, String mensaje) {
        super(HttpStatus.CONFLICT, codigo, mensaje);
    }
}
