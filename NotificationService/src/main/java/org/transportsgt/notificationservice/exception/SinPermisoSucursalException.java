package org.transportsgt.notificationservice.exception;

/**
 * 403: un administrador de sucursal pidió datos de otra sucursal.
 */
public class SinPermisoSucursalException extends AccesoDenegadoException {

    public SinPermisoSucursalException() {
        super("SIN_PERMISO", "Solo puede consultar los avisos de su sucursal");
    }
}
