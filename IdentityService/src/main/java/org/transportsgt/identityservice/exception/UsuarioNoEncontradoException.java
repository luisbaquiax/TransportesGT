package org.transportsgt.identityservice.exception;

import java.util.UUID;

public class UsuarioNoEncontradoException extends RecursoNoEncontradoException {

    public UsuarioNoEncontradoException(UUID id) {
        super("USUARIO_NO_ENCONTRADO", "No se encontró el usuario " + id);
    }
}
