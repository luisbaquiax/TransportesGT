package org.transportsgt.reportingservice.exception;

/**
 * El administrador del sistema abrió un reporte de sucursal (o el mapa de rutas) sin indicar {@code idSucursal}.
 */
public class SucursalRequeridaException extends SolicitudInvalidaException {

    public SucursalRequeridaException() {
        super("VALIDACION", "Debe indicar el parámetro 'idSucursal'");
    }
}
