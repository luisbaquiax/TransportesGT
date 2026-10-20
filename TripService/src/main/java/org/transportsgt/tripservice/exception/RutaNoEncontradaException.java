package org.transportsgt.tripservice.exception;

import org.springframework.http.HttpStatus;

public class RutaNoEncontradaException extends ViajeException {
    public RutaNoEncontradaException(java.util.UUID id) {
        super(HttpStatus.NOT_FOUND, "RUTA_NO_ENCONTRADA", "La ruta " + id + " no existe");
    }
}
