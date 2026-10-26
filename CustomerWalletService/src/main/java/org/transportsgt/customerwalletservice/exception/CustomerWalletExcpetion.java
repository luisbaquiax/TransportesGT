package org.transportsgt.customerwalletservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Exception global para el servicio de Customer Wallet.
 */
@Getter
public class CustomerWalletExcpetion extends RuntimeException {
    private final HttpStatus status;

    /**
     * Código estable para el cliente
     */
    private final String codigo;

    protected CustomerWalletExcpetion(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }
}
