package org.transportsgt.customerwalletservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 422: la petición es válida, pero una regla de negocio la impide.
 */
public abstract class ReglaNegocioException extends CustomerWalletExcpetion {

    protected ReglaNegocioException(String codigo, String mensaje) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, codigo, mensaje);
    }
}
