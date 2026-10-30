package org.transportsgt.reportingservice.exception;

import org.springframework.http.HttpStatus;

/** Fallo interno al compilar, llenar o exportar un reporte Jasper. */
public class GeneracionReporteException extends ReportingServiceException {
    public GeneracionReporteException(String mensaje, Throwable causa) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "ERROR_GENERANDO_REPORTE", mensaje);
        initCause(causa);
    }
}
