package org.transportsgt.identityservice.exception;

import org.springframework.http.HttpStatus;

/**
 * 503: una dependencia no está disponible.
 */
public abstract class ServicioNoDisponibleException extends IdentityServiceException {

    protected ServicioNoDisponibleException(String codigo, String mensaje) {
        super(HttpStatus.SERVICE_UNAVAILABLE, codigo, mensaje);
    }
}
