package org.transportsgt.privaterentalservice.exception;

import org.springframework.http.HttpStatus;

/** Excepción base del dominio de alquileres. */
public class AlquilerException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    public AlquilerException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }

    public AlquilerException(HttpStatus status, String codigo, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }
}
