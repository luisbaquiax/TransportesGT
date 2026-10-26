package org.transportsgt.reportingservice.exception;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

/**
 * Cuerpo de toda respuesta de error del servicio. Lo usan el handler global,
 * el AuthenticationEntryPoint (401) y el AccessDeniedHandler (403).
 *
 * @param error  mensaje legible para el usuario
 * @param codigo código estable, p. ej. {@code USUARIO_NO_ENCONTRADO}
 * @param ruta   URI de la petición
 */
public record RespuestaError(
        LocalDateTime timestamp,
        int status,
        String error,
        String codigo,
        String ruta
) {

    public static RespuestaError de(HttpStatus status, String error, String codigo, String ruta) {
        return new RespuestaError(LocalDateTime.now(), status.value(), error, codigo, ruta);
    }
}
