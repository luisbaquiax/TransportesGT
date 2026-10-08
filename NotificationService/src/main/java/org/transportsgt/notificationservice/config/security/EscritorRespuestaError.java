package org.transportsgt.notificationservice.config.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.transportsgt.notificationservice.exception.RespuestaError;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Escribe {@link RespuestaError} desde los filtros de seguridad, donde el @RestControllerAdvice no llega.
 */
@Component
@RequiredArgsConstructor
public class EscritorRespuestaError {

    private final JsonMapper jsonMapper;

    public void escribir(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                         String mensaje, String codigo) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        RespuestaError cuerpo = RespuestaError.de(status, mensaje, codigo, request.getRequestURI());
        response.getWriter().write(jsonMapper.writeValueAsString(cuerpo));
    }
}
