package org.transportsgt.ticketservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 404: el recurso no existe (o no le corresponde al usuario).
 */
public abstract class RecursoNoEncontradoException extends BoletosServiceException {

    protected RecursoNoEncontradoException(String codigo, String mensaje) {
        super(HttpStatus.NOT_FOUND, codigo, mensaje);
    }
}
