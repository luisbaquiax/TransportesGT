package org.transportsgt.fleetservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 503: una dependencia no está disponible.
 */
public abstract class ServicioNoDisponibleException extends FlotaException {

    protected ServicioNoDisponibleException(String codigo, String mensaje) {
        super(HttpStatus.SERVICE_UNAVAILABLE, codigo, mensaje);
    }
}
