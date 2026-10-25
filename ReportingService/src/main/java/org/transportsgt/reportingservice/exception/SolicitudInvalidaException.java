package org.transportsgt.reportingservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 400: la petición está mal formada o no cumple una regla de formato.
 */
public abstract class SolicitudInvalidaException extends ReportingServiceException {

    protected SolicitudInvalidaException(String codigo, String mensaje) {
        super(HttpStatus.BAD_REQUEST, codigo, mensaje);
    }
}
