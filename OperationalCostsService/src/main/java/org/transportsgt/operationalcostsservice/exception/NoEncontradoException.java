package org.transportsgt.operationalcostsservice.exception;

import org.springframework.http.HttpStatus;

public class NoEncontradoException extends CostosException {
    public NoEncontradoException(String codigo, String mensaje) {
        super(HttpStatus.NOT_FOUND, codigo, mensaje);
    }

    public NoEncontradoException(String codigo, String mensaje, Throwable causa) {
        super(HttpStatus.NOT_FOUND, codigo, mensaje, causa);
    }
}
