package org.transportsgt.reportingservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 403: el usuario está autenticado, pero no puede hacer la operación.
 */
public abstract class AccesoDenegadoException extends ReportingServiceException {

    protected AccesoDenegadoException(String codigo, String mensaje) {
        super(HttpStatus.FORBIDDEN, codigo, mensaje);
    }
}
