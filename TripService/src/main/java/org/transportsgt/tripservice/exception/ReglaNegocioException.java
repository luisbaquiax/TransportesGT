package org.transportsgt.tripservice.exception;

import org.springframework.http.HttpStatus;

public class ReglaNegocioException extends ViajeException {
    public ReglaNegocioException(String codigo, String mensaje) {
        super(HttpStatus.CONFLICT, codigo, mensaje);
    }
}
