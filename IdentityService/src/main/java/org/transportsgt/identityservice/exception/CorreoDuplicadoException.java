package org.transportsgt.identityservice.exception;

public class CorreoDuplicadoException extends ConflictoException {

    public CorreoDuplicadoException(String correo) {
        super("CORREO_DUPLICADO", "Ya existe un usuario con el correo " + correo);
    }
}
