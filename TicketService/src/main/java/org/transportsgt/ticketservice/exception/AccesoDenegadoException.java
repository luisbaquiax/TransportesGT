package org.transportsgt.ticketservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 403: el usuario está autenticado, pero no puede hacer la operación.
 */
public abstract class AccesoDenegadoException extends BoletosServiceException {

    protected AccesoDenegadoException(String codigo, String mensaje) {
        super(HttpStatus.FORBIDDEN, codigo, mensaje);
    }
}
