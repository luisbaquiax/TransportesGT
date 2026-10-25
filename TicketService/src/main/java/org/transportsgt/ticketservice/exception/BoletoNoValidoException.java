package org.transportsgt.ticketservice.exception;

/**
 * El boleto no sirve para abordar: está anulado, sin pagar o es de otro viaje.
 */
public class BoletoNoValidoException extends ConflictoException {

    public BoletoNoValidoException(String motivo) {
        super("BOLETO_NO_VALIDO", motivo);
    }
}
