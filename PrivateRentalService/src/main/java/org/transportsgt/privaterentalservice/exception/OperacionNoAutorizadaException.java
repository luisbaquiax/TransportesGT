package org.transportsgt.privaterentalservice.exception;

import org.springframework.http.HttpStatus;

public class OperacionNoAutorizadaException extends AlquilerException {
    public OperacionNoAutorizadaException(String mensaje) {
        super(HttpStatus.FORBIDDEN, "OPERACION_NO_AUTORIZADA", mensaje);
    }
}
