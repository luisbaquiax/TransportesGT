package org.transportsgt.apigateway.exception;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

/**
 * Cuerpo de toda respuesta de error del gateway. Lo usan el AuthenticationEntryPoint (401)
 * y el AccessDeniedHandler (403), para que el frontend reciba siempre el mismo JSON
 * {timestamp, status, error, codigo, ruta} que devuelven los microservicios.
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
