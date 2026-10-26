package org.transportsgt.customerwalletservice.exception;

/**
 * No hay PAGO para la referencia, o es de otra cartera.
 */
public class PagoNoEncontradoException extends RecursoNoEncontradoException {

    public PagoNoEncontradoException() {
        super("NO_ENCONTRADO", "No existe un pago para esa compra o alquiler");
    }
}
