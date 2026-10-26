package org.transportsgt.ticketservice.exception;

/**
 * Un administrador de sucursal o cajero solo vende y consulta viajes de su sucursal.
 */
public class SinPermisoSucursalException extends AccesoDenegadoException {

    public SinPermisoSucursalException() {
        super("SIN_PERMISO", "El viaje es de otra sucursal");
    }
}
