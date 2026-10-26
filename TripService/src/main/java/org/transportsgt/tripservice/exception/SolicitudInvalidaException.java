package org.transportsgt.tripservice.exception;

import org.springframework.http.HttpStatus;

public class SolicitudInvalidaException extends ViajeException {
    public SolicitudInvalidaException(String mensaje) {
        super(HttpStatus.BAD_REQUEST, "SOLICITUD_INVALIDA", mensaje);
    }
}
