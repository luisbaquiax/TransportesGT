package org.transportsgt.ticketservice.exception;

/**
 * Se reintentó una compra que ya está {@code ANULADA}.
 */
public class CompraAnuladaException extends ConflictoException {

    public CompraAnuladaException(String motivo) {
        super("COMPRA_ANULADA", "La compra está anulada" + (motivo == null ? "" : " (" + motivo + ")"));
    }
}
