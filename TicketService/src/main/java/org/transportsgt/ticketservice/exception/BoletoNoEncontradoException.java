package org.transportsgt.ticketservice.exception;

/**
 * Ningún boleto tiene ese código.
 */
public class BoletoNoEncontradoException extends RecursoNoEncontradoException {

    public BoletoNoEncontradoException(String codigo) {
        super("NO_ENCONTRADO", "No existe un boleto con el código " + codigo);
    }
}
