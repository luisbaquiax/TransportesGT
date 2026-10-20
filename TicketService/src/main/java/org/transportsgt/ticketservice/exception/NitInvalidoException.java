package org.transportsgt.ticketservice.exception;

/**
 * El NIT de una venta de mostrador no tiene el formato válido (2 a 20 letras o dígitos, sin contar guiones).
 */
public class NitInvalidoException extends SolicitudInvalidaException {

    public NitInvalidoException() {
        super("VALIDACION_FALLIDA", "El NIT debe tener de 2 a 20 letras o dígitos");
    }
}
