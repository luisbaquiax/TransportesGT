package org.transportsgt.ticketservice.exception;

/**
 * El viaje no se puede vender: no existe a la venta, se eliminó, no está {@code PROGRAMADO} o ya salió.
 */
public class ViajeNoDisponibleException extends ConflictoException {

    public ViajeNoDisponibleException(String motivo) {
        super("VIAJE_NO_DISPONIBLE", motivo);
    }
}
