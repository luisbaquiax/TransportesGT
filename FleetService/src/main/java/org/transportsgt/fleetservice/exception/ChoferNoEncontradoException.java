package org.transportsgt.fleetservice.exception;

import java.util.UUID;

public class ChoferNoEncontradoException extends RecursoNoEncontradoException {

    public ChoferNoEncontradoException(UUID id) {
        super("CHOFER_NO_ENCONTRADO", "No se encontró el chofer " + id);
    }
}
