package org.transportsgt.customerwalletservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 404: el recurso no existe (o no pertenece al usuario).
 */
public abstract class RecursoNoEncontradoException extends CustomerWalletExcpetion {

    protected RecursoNoEncontradoException(String codigo, String mensaje) {
        super(HttpStatus.NOT_FOUND, codigo, mensaje);
    }
}
