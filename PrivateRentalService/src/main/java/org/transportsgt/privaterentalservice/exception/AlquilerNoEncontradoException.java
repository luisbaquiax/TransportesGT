package org.transportsgt.privaterentalservice.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class AlquilerNoEncontradoException extends AlquilerException {
    public AlquilerNoEncontradoException(UUID id) {
        super(HttpStatus.NOT_FOUND, "ALQUILER_NO_ENCONTRADO", "No existe el alquiler " + id);
    }
}
