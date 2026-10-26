package org.transportsgt.fleetservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 422: la solicitud es válida pero viola una regla de negocio.
 */
public abstract class ReglaNegocioException extends FlotaException {

    protected ReglaNegocioException(String codigo, String mensaje) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, codigo, mensaje);
    }
}
