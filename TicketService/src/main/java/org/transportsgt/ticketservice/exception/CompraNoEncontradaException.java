package org.transportsgt.ticketservice.exception;

/**
 * La compra no existe o no le corresponde al usuario.
 */
public class CompraNoEncontradaException extends RecursoNoEncontradoException {

    public CompraNoEncontradaException() {
        super("NO_ENCONTRADO", "La compra no existe");
    }
}
