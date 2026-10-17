package org.transportsgt.fleetservice.exception;

public class SucursalInactivaException extends ReglaNegocioException {

    public SucursalInactivaException(String id) {
        super("SUCURSAL_INACTIVA", "La sucursal " + id + " está desactivada");
    }
}
