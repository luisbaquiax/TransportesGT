package org.transportsgt.identityservice.exception;

public class UsuarioInactivoException extends AccesoDenegadoException {

    public UsuarioInactivoException() {
        super("USUARIO_INACTIVO", "El usuario está desactivado");
    }
}
