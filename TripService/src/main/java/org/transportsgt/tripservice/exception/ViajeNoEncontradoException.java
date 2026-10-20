package org.transportsgt.tripservice.exception;

import org.springframework.http.HttpStatus;

public class ViajeNoEncontradoException extends ViajeException {
    public ViajeNoEncontradoException(java.util.UUID id) {
        super(HttpStatus.NOT_FOUND, "VIAJE_NO_ENCONTRADO", "El viaje " + id + " no existe");
    }
}
