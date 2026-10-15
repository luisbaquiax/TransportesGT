package org.transportsgt.customerwalletservice.exception;

/**
 * NIT con formato inválido o {@code CF} (consumidor final, reservado para el mostrador).
 */
public class NitInvalidoException extends SolicitudInvalidaException {

    public NitInvalidoException() {
        super("VALIDACION_FALLIDA", "El NIT debe tener de 2 a 20 letras o dígitos y no puede ser CF");
    }
}
