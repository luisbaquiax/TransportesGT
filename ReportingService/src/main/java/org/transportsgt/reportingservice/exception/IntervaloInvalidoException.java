package org.transportsgt.reportingservice.exception;

/**
 * Filtro de fechas con {@code desde} posterior a {@code hasta}.
 */
public class IntervaloInvalidoException extends SolicitudInvalidaException {

    public IntervaloInvalidoException() {
        super("VALIDACION", "La fecha 'desde' no puede ser posterior a 'hasta'");
    }
}
