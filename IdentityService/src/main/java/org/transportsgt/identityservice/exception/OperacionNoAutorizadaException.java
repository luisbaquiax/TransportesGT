package org.transportsgt.identityservice.exception;

public class OperacionNoAutorizadaException extends AccesoDenegadoException {

    public OperacionNoAutorizadaException(String mensaje) {
        super("OPERACION_NO_AUTORIZADA", mensaje);
    }
}
