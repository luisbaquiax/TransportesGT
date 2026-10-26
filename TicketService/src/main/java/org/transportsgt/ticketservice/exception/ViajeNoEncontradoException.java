package org.transportsgt.ticketservice.exception;

/**
 * El viaje no está en la proyección {@code viaje_ref}.
 */
public class ViajeNoEncontradoException extends RecursoNoEncontradoException {

    public ViajeNoEncontradoException() {
        super("NO_ENCONTRADO", "El viaje no existe");
    }
}
