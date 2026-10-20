package org.transportsgt.notificationservice.exception;

public class ReintentoNoPermitidoException extends ConflictoException {

    public ReintentoNoPermitidoException(String motivo) {
        super("REINTENTO_NO_PERMITIDO", motivo);
    }
}
