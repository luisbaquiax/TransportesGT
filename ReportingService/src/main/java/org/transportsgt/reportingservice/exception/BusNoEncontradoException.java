package org.transportsgt.reportingservice.exception;

import java.util.UUID;

/**
 * El bus aún no está en {@code rep_bus}.
 */
public class BusNoEncontradoException extends RecursoNoEncontradoException {

    public BusNoEncontradoException(UUID idBus) {
        super("NO_ENCONTRADO", "No existe el bus " + idBus);
    }
}
