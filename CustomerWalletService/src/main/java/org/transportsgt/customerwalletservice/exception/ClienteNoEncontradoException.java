package org.transportsgt.customerwalletservice.exception;

/**
 * El usuario aún no tiene perfil de cliente (se crea al consumir UsuarioCreado).
 */
public class ClienteNoEncontradoException extends RecursoNoEncontradoException {

    public ClienteNoEncontradoException() {
        super("NO_ENCONTRADO", "El usuario no tiene perfil de cliente");
    }
}
