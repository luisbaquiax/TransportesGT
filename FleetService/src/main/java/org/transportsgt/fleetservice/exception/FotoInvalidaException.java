package org.transportsgt.fleetservice.exception;

/**
 * 400: la imagen no cumple las reglas (tipo no permitido, vacía o demasiado grande).
 */
public class FotoInvalidaException extends SolicitudInvalidaException {

    public FotoInvalidaException(String mensaje) {
        super("FOTO_INVALIDA", mensaje);
    }
}
