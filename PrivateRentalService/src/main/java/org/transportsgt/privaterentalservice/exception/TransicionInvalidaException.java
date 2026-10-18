package org.transportsgt.privaterentalservice.exception;

import org.springframework.http.HttpStatus;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;

public class TransicionInvalidaException extends AlquilerException {
    public TransicionInvalidaException(EstadoAlquiler actual, String operacion) {
        super(HttpStatus.CONFLICT, "TRANSICION_INVALIDA",
                "Un alquiler en estado " + actual + " no puede: " + operacion);
    }
}
