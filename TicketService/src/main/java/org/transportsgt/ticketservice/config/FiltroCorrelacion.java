package org.transportsgt.ticketservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.transportsgt.eventos.ContextoCorrelacion;

import java.io.IOException;
import java.util.UUID;

/**
 * Fija el {@code idCorrelacion} de la petición (cabecera {@code X-Correlation-Id} o uno nuevo).
 * RegistroOutbox lo copia a cada evento y aparece en los logs (MDC).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroCorrelacion extends OncePerRequestFilter {

    public static final String CABECERA = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID idCorrelacion = leer(request.getHeader(CABECERA));
        ContextoCorrelacion.fijar(idCorrelacion);
        response.setHeader(CABECERA, idCorrelacion.toString());
        try {
            chain.doFilter(request, response);
        } finally {
            ContextoCorrelacion.limpiar();
        }
    }

    private static UUID leer(String valor) {
        if (valor != null) {
            try {
                return UUID.fromString(valor.trim());
            } catch (IllegalArgumentException ignorado) {
                // valor no válido: se genera uno nuevo
            }
        }
        return UUID.randomUUID();
    }
}
