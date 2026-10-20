package org.transportsgt.tripservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Excepción de dominio de Viajes: código de negocio + status HTTP. */
@Getter
public abstract class ViajeException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    protected ViajeException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }
}
