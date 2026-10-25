package org.transportsgt.reportingservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 404: el recurso no existe en las proyecciones de Reportes.
 */
public abstract class RecursoNoEncontradoException extends ReportingServiceException {

    protected RecursoNoEncontradoException(String codigo, String mensaje) {
        super(HttpStatus.NOT_FOUND, codigo, mensaje);
    }
}
