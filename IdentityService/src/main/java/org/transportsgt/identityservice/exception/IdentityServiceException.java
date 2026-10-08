package org.transportsgt.identityservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base de todas las excepciones de negocio del servicio de identidad.
 * El handler global responde con {@link #getStatus()} y {@link #getCodigo()}.
 */
@Getter
public abstract class IdentityServiceException extends RuntimeException {

    private final HttpStatus status;

    /**
     * Código estable para el cliente, p. ej. {@code USUARIO_NO_ENCONTRADO}.
     */
    private final String codigo;

    protected IdentityServiceException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }
}
