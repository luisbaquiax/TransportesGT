package org.transportsgt.operationalcostsservice.exception;

import org.springframework.http.HttpStatus;

public class ReglaNegocioException extends CostosException {
    public ReglaNegocioException(String codigo, String mensaje) {
        super(HttpStatus.CONFLICT, codigo, mensaje);
    }
}
