package org.transportsgt.identityservice.exception;

public class TokenContrasenaInvalidoException extends RecursoExpiradoException {

    public TokenContrasenaInvalidoException() {
        super("ENLACE_INVALIDO", "El enlace no es válido, ya se usó o expiró");
    }
}
