package org.transportsgt.operationalcostsservice.exception;

import org.springframework.http.HttpStatus;

public class OperacionNoAutorizadaException extends CostosException {
    public OperacionNoAutorizadaException(String mensaje) {
        super(HttpStatus.FORBIDDEN, "OPERACION_NO_AUTORIZADA", mensaje);
    }
}
