package org.transportsgt.customerwalletservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 503: una dependencia no respondió.
 */
public abstract class ServicioNoDisponibleException extends CustomerWalletExcpetion {

    protected ServicioNoDisponibleException(String codigo, String mensaje) {
        super(HttpStatus.SERVICE_UNAVAILABLE, codigo, mensaje);
    }
}
