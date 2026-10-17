package org.transportsgt.customerwalletservice.exception;

/**
 * La cartera no alcanza para el pago.
 */
public class SaldoInsuficienteException extends ReglaNegocioException {

    public SaldoInsuficienteException() {
        super("SALDO_INSUFICIENTE", "El saldo de la cartera no alcanza para el pago");
    }
}
