package org.transportsgt.tripservice.exception;

import org.springframework.http.HttpStatus;

public class ServicioNoDisponibleException extends ViajeException {
    public ServicioNoDisponibleException(String mensaje) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICIO_NO_DISPONIBLE", mensaje);
    }

    public ServicioNoDisponibleException(String mensaje, Throwable causa) {
        this(mensaje);
        initCause(causa);
    }
}
