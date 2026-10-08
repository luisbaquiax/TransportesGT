package org.transportsgt.identityservice.exception;

public class RolNoPermitidoException extends ReglaNegocioException {

    public RolNoPermitidoException(String mensaje) {
        super("ROL_NO_PERMITIDO", mensaje);
    }
}
