package org.transportsgt.fleetservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base de todas las excepciones de negocio del servicio de flota.
 * El handler global responde con {@link #getStatus()} y {@link #getCodigo()}.
 */
@Getter
public abstract class FlotaException extends RuntimeException {

    private final HttpStatus status;

    /**
     * Código estable para el cliente, p. ej. {@code BUS_NO_ENCONTRADO}.
     */
    private final String codigo;

    protected FlotaException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }
}
