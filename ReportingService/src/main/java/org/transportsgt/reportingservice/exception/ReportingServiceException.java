package org.transportsgt.reportingservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Excepción base del servicio de reportes.
 */
@Getter
public class ReportingServiceException extends RuntimeException {
    private final HttpStatus status;

    /**
     * Código estable para el cliente
     */
    private final String codigo;

    protected ReportingServiceException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }
}
