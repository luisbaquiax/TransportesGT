package org.transportsgt.ticketservice.exception;

/**
 * Asientos vacíos, repetidos, fuera de la capacidad del bus o más del máximo por compra.
 */
public class AsientoInvalidoException extends SolicitudInvalidaException {

    public AsientoInvalidoException(String detalle) {
        super("VALIDACION_FALLIDA", detalle);
    }
}
