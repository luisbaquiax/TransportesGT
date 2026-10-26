package org.transportsgt.customerwalletservice.exception;

/**
 * La misma Clave-Idempotencia llegó con otra cartera u otro monto.
 */
public class ClaveIdempotenciaReutilizadaException extends ConflictoException {

    public ClaveIdempotenciaReutilizadaException() {
        super("CLAVE_IDEMPOTENCIA_REUTILIZADA", "La Clave-Idempotencia ya se usó con otros datos");
    }
}
