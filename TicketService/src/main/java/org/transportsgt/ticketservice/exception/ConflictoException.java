package org.transportsgt.ticketservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 409: la operación choca con el estado actual de los datos.
 */
public abstract class ConflictoException extends BoletosServiceException {

    protected ConflictoException(String codigo, String mensaje) {
        super(HttpStatus.CONFLICT, codigo, mensaje);
    }
}
