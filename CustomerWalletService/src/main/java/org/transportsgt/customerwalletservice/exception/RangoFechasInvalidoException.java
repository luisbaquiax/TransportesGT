package org.transportsgt.customerwalletservice.exception;

/**
 * Filtro de fechas con {@code desde} posterior a {@code hasta}.
 */
public class RangoFechasInvalidoException extends SolicitudInvalidaException {

    public RangoFechasInvalidoException() {
        super("SOLICITUD_INVALIDA", "La fecha 'desde' no puede ser posterior a 'hasta'");
    }
}
