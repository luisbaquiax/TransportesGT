package org.transportsgt.customerwalletservice.exception;

/**
 * El NIT ya lo tiene otro cliente ({@code uq_cliente_nit}).
 */
public class NitDuplicadoException extends ConflictoException {

    public NitDuplicadoException() {
        super("NIT_DUPLICADO", "Ya existe un cliente con ese NIT");
    }
}
