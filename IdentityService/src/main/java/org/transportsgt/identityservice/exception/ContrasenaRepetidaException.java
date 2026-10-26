package org.transportsgt.identityservice.exception;

public class ContrasenaRepetidaException extends ReglaNegocioException {

    public ContrasenaRepetidaException() {
        super("CONTRASENA_REPETIDA", "La nueva contraseña debe ser distinta de la actual");
    }
}
