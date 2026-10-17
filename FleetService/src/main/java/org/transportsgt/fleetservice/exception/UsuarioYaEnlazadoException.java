package org.transportsgt.fleetservice.exception;

import java.util.UUID;

public class UsuarioYaEnlazadoException extends ConflictoException {

    public UsuarioYaEnlazadoException(UUID idUsuario) {
        super("USUARIO_YA_ENLAZADO", "El usuario " + idUsuario + " ya está enlazado a otro chofer");
    }
}
