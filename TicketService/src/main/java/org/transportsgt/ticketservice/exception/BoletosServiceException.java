package org.transportsgt.ticketservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Excepción base del servicio de boletos.
 */
@Getter
public class BoletosServiceException extends RuntimeException {
    private final HttpStatus status;

    /**
     * Código estable para el cliente
     */
    private final String codigo;

    protected BoletosServiceException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }
}
