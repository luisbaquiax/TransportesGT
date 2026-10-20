package org.transportsgt.ticketservice.exception;

/**
 * El mismo {@code idCompra} llegó con otro usuario, otro viaje u otros asientos.
 */
public class CompraDuplicadaException extends ConflictoException {

    public CompraDuplicadaException() {
        super("COMPRA_DUPLICADA", "Ya existe una compra con ese idCompra y otros datos");
    }
}
