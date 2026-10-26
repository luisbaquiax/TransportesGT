package org.transportsgt.identityservice.exception;

import java.util.UUID;

public class SucursalInactivaException extends ReglaNegocioException {

    public SucursalInactivaException(UUID id) {
        super("SUCURSAL_INACTIVA", "La sucursal " + id + " está desactivada");
    }
}
