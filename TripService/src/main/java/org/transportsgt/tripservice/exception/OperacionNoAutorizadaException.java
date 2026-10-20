package org.transportsgt.tripservice.exception;

import org.springframework.http.HttpStatus;

public class OperacionNoAutorizadaException extends ViajeException {
    public OperacionNoAutorizadaException(String mensaje) {
        super(HttpStatus.FORBIDDEN, "OPERACION_NO_AUTORIZADA", mensaje);
    }
}
