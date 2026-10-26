package org.transportsgt.identityservice.exception;

public class UltimoAdministradorSistemaException extends ReglaNegocioException {

    public UltimoAdministradorSistemaException() {
        super("ULTIMO_ADMIN_SISTEMA", "Debe existir al menos un administrador de sistema activo");
    }
}
