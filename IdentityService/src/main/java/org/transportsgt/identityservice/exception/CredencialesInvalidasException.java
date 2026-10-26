package org.transportsgt.identityservice.exception;

public class CredencialesInvalidasException extends NoAutenticadoException {

    public CredencialesInvalidasException() {
        super("CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos");
    }
}
