package org.transportsgt.identityservice.exception;

public class SucursalNoPermitidaException extends ReglaNegocioException {

    public SucursalNoPermitidaException(String mensaje) {
        super("SUCURSAL_NO_PERMITIDA", mensaje);
    }
}
