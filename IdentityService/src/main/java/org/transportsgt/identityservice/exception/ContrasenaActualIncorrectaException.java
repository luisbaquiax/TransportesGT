package org.transportsgt.identityservice.exception;

public class ContrasenaActualIncorrectaException extends ReglaNegocioException {

    public ContrasenaActualIncorrectaException() {
        super("CONTRASENA_ACTUAL_INCORRECTA", "La contraseña actual no es correcta");
    }
}
