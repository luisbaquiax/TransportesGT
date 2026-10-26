package org.transportsgt.privaterentalservice.exception;

import org.springframework.http.HttpStatus;

public class ReglaNegocioException extends AlquilerException {
    public ReglaNegocioException(String codigo, String mensaje) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, codigo, mensaje);
    }
}
