package org.transportsgt.identityservice.exception;

public class UltimoAdministradorSucursalException extends ReglaNegocioException {

    public UltimoAdministradorSucursalException() {
        super("ULTIMO_ADMIN_SUCURSAL", "La sucursal debe conservar al menos un administrador de sucursal activo");
    }
}
