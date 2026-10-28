package org.transportsgt.notificationservice.config.security.handlers;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.transportsgt.notificationservice.exception.RespuestaError;
import org.transportsgt.notificationservice.exception.FabricaRespuestaError;
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
