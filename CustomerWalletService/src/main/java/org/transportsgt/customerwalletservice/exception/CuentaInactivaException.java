package org.transportsgt.customerwalletservice.exception;

/**
 * El usuario está desactivado: no puede pagar ni recargar.
 */
public class CuentaInactivaException extends AccesoDenegadoException {

    public CuentaInactivaException() {
        super("CUENTA_INACTIVA", "La cuenta está desactivada");
    }
}
