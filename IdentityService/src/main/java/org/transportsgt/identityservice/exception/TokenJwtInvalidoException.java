package org.transportsgt.identityservice.exception;

public class TokenJwtInvalidoException extends NoAutenticadoException {

    public TokenJwtInvalidoException() {
        super("TOKEN_INVALIDO", "El token de acceso no es válido o expiró");
    }
}
