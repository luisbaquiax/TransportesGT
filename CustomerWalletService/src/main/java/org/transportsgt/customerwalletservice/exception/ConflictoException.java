package org.transportsgt.customerwalletservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 409: la operación choca con datos que ya existen.
 */
public abstract class ConflictoException extends CustomerWalletExcpetion {

    protected ConflictoException(String codigo, String mensaje) {
        super(HttpStatus.CONFLICT, codigo, mensaje);
    }
}
