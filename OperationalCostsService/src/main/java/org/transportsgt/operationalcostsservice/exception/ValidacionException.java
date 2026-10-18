package org.transportsgt.operationalcostsservice.exception;

import org.springframework.http.HttpStatus;

public class ValidacionException extends CostosException {
    public ValidacionException(String codigo, String mensaje) {
        super(HttpStatus.BAD_REQUEST, codigo, mensaje);
    }
}
