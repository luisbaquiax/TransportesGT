package org.transportsgt.reportingservice.exception;

/**
 * El administrador de sucursal pidió explícitamente datos de otra sucursal (o un bus o ruta ajenos).
 */
public class SinPermisoSucursalException extends AccesoDenegadoException {

    public SinPermisoSucursalException() {
        super("SIN_PERMISO", "Solo puede consultar los reportes de su sucursal");
    }
}
