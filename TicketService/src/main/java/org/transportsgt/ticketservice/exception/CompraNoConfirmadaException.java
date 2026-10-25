package org.transportsgt.ticketservice.exception;

/**
 * Solo una compra {@code CONFIRMADA} tiene boletos para imprimir.
 */
public class CompraNoConfirmadaException extends ConflictoException {

    public CompraNoConfirmadaException(String estado) {
        super("COMPRA_NO_CONFIRMADA", "La compra está " + estado + ": solo una compra confirmada tiene boletos");
    }
}
