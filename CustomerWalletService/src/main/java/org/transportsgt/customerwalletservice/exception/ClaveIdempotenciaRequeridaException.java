package org.transportsgt.customerwalletservice.exception;

/**
 * Las operaciones que mueven dinero exigen la cabecera {@code Clave-Idempotencia}.
 */
public class ClaveIdempotenciaRequeridaException extends SolicitudInvalidaException {

    public ClaveIdempotenciaRequeridaException() {
        super("SOLICITUD_INVALIDA", "Falta la cabecera Clave-Idempotencia");
    }
}
