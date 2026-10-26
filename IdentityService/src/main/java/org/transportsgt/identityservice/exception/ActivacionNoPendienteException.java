package org.transportsgt.identityservice.exception;

public class ActivacionNoPendienteException extends ReglaNegocioException {

    public ActivacionNoPendienteException() {
        super("ACTIVACION_NO_PENDIENTE", "El usuario ya definió su contraseña o está desactivado");
    }
}
