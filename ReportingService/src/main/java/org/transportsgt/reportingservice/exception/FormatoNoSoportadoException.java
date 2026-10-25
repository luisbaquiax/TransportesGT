package org.transportsgt.reportingservice.exception;

/**
 * {@code formato} distinto de {@code json} y {@code pdf}, o un formato que aún no está disponible.
 */
public class FormatoNoSoportadoException extends SolicitudInvalidaException {

    public FormatoNoSoportadoException(String formato) {
        super("VALIDACION", "El formato '" + formato + "' no está soportado; use json o pdf");
    }
}
