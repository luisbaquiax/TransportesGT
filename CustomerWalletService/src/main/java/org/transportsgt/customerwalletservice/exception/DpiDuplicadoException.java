package org.transportsgt.customerwalletservice.exception;

/**
 * El DPI ya lo tiene otro cliente ({@code uq_cliente_dpi}).
 */
public class DpiDuplicadoException extends ConflictoException {

    public DpiDuplicadoException() {
        super("DPI_DUPLICADO", "Ya existe un cliente con ese DPI");
    }
}
