package org.transportsgt.fleetservice.exception;

/**
 * 409 TRANSICION_INVALIDA: el estado no puede cambiar por endpoint
 * ({@code EN_TRANSITO} solo lo mueven la salida y la llegada de un viaje).
 */
public class EstadoOperativoInvalidoException extends ConflictoException {

    public EstadoOperativoInvalidoException(String mensaje) {
        super("TRANSICION_INVALIDA", mensaje);
    }
}
