package org.transportsgt.identityservice.exception;

public class UsuarioInactivoException extends AccesoDenegadoException {

    public UsuarioInactivoException() {
        super("CUENTA_INACTIVA", "El usuario está desactivado");
    }
}
