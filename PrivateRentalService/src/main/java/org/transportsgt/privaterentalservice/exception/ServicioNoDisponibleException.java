package org.transportsgt.privaterentalservice.exception;

import org.springframework.http.HttpStatus;

public class ServicioNoDisponibleException extends AlquilerException {
    public ServicioNoDisponibleException(String servicio, Throwable causa) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICIO_NO_DISPONIBLE",
                servicio + " no está disponible en este momento", causa);
    }

    public ServicioNoDisponibleException(String mensaje) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "SERVICIO_NO_DISPONIBLE", mensaje);
    }
}
