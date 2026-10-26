package org.transportsgt.privaterentalservice.exception;

import org.springframework.http.HttpStatus;

public class CarteraRechazoException extends AlquilerException {
    public CarteraRechazoException(HttpStatus status, String codigo, String mensaje) {
        super(status, codigo, mensaje);
    }
}
