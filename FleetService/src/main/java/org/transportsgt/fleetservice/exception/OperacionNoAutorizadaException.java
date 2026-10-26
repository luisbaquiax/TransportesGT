package org.transportsgt.fleetservice.exception;

public class OperacionNoAutorizadaException extends AccesoDenegadoException {

    public OperacionNoAutorizadaException(String mensaje) {
        super("OPERACION_NO_AUTORIZADA", mensaje);
    }
}
