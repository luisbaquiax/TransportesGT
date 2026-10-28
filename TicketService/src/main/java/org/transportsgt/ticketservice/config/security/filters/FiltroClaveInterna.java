package org.transportsgt.ticketservice.config.security.filters;

import org.transportsgt.ticketservice.config.security.handlers.EscritorRespuestaError;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Credencial de las llamadas internas sin usuario (Viajes, al cambiar el bus de un viaje).
 *
 * <p>Solo actúa en {@code GET /boletos/viajes/{idViaje}/asiento-maximo} cuando la petición trae {@code X-Clave-Interna} y NO trae
 * {@code Authorization}. Si la clave coincide (comparación en tiempo constante) autentica con {@code ROLE_INTERNO};
 * si no, responde 401. En cualquier otra ruta la cabecera se ignora. Va antes del filtro del bearer token.
 */
@Component
public class FiltroClaveInterna extends OncePerRequestFilter {

    public static final String CABECERA = "X-Clave-Interna";
    public static final String ROL_INTERNO = "ROLE_INTERNO";
    static final Pattern RUTA = Pattern.compile(
            "^/boletos/viajes/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/asiento-maximo$");

    private final byte[] clave;
    private final EscritorRespuestaError escritor;

    public FiltroClaveInterna(@Value("${seguridad.clave-interna}") String clave, EscritorRespuestaError escritor) {
        if (clave == null || clave.isBlank()) {
            throw new IllegalStateException("Falta la variable CLAVE_INTERNA (seguridad.clave-interna)");
        }
        this.clave = clave.getBytes(StandardCharsets.UTF_8);
        this.escritor = escritor;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.GET.matches(request.getMethod())
                || !RUTA.matcher(rutaSinContexto(request)).matches()
                || request.getHeader(CABECERA) == null
                || request.getHeader(HttpHeaders.AUTHORIZATION) != null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] recibida = request.getHeader(CABECERA).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(clave, recibida)) {
            escritor.escribir(request, response, HttpStatus.UNAUTHORIZED, "La clave interna no es válida",
                    "NO_AUTENTICADO");
            return;
        }
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "interno", null, List.of(new SimpleGrantedAuthority(ROL_INTERNO))));
        SecurityContextHolder.setContext(contexto);
        chain.doFilter(request, response);
    }

    private String rutaSinContexto(HttpServletRequest request) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        return ruta.endsWith("/") && ruta.length() > 1 ? ruta.substring(0, ruta.length() - 1) : ruta;
    }
}
