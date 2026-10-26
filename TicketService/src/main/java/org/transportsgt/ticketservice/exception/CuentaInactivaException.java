package org.transportsgt.ticketservice.exception;

/**
 * El comprador está desactivado: no puede comprar en línea.
 */
public class CuentaInactivaException extends AccesoDenegadoException {

    public CuentaInactivaException() {
        super("CUENTA_INACTIVA", "La cuenta está desactivada");
    }
}
