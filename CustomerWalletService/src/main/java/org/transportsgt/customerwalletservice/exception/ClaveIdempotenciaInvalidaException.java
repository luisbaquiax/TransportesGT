package org.transportsgt.customerwalletservice.exception;

/**
 * En {@code POST /cartera/pagos} la clave debe ser el idCompra o idAlquiler.
 */
public class ClaveIdempotenciaInvalidaException extends SolicitudInvalidaException {

    public ClaveIdempotenciaInvalidaException() {
        super("SOLICITUD_INVALIDA", "En un pago, la Clave-Idempotencia debe ser igual a idReferencia");
    }
}
