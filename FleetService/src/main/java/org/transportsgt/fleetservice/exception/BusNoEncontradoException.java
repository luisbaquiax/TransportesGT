package org.transportsgt.fleetservice.exception;

import java.util.UUID;

public class BusNoEncontradoException extends RecursoNoEncontradoException {

    public BusNoEncontradoException(UUID id) {
        super("BUS_NO_ENCONTRADO", "No se encontró el bus " + id);
    }
}
