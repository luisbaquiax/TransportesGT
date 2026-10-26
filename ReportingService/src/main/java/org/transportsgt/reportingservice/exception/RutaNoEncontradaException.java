package org.transportsgt.reportingservice.exception;

import java.util.UUID;

/**
 * La ruta aún no está en {@code rep_ruta}.
 */
public class RutaNoEncontradaException extends RecursoNoEncontradoException {

    public RutaNoEncontradaException(UUID idRuta) {
        super("NO_ENCONTRADO", "No existe la ruta " + idRuta);
    }
}
