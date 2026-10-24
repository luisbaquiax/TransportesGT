package org.transportsgt.apigateway.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.transportsgt.apigateway.exception.FabricaRespuestaError;
import org.transportsgt.apigateway.exception.RespuestaError;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Escribe {@link RespuestaError} desde los filtros de seguridad del gateway
 * (sin esto, Spring responde 401/403 con cuerpo vacío y el frontend no puede mostrar un mensaje).
 */
@Component
@RequiredArgsConstructor
public class EscritorRespuestaError {

    private final JsonMapper jsonMapper;
    private final FabricaRespuestaError fabricaRespuestaError;

    public void escribir(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                         String mensaje, String codigo) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        RespuestaError cuerpo = fabricaRespuestaError.crear(status, mensaje, codigo, request.getRequestURI());
        response.getWriter().write(jsonMapper.writeValueAsString(cuerpo));
    }
}
