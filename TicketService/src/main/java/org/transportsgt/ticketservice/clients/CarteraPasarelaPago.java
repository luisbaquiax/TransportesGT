package org.transportsgt.ticketservice.clients;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.transportsgt.ticketservice.clients.dto.PagoCarteraRequest;
import org.transportsgt.ticketservice.exception.CarteraNoDisponibleException;
import org.transportsgt.ticketservice.exception.PagoRechazadoException;
import org.transportsgt.eventos.payload.TipoReferencia;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Adaptador de {@link PasarelaPago} sobre Cartera: Resilience4j (instancias {@code cartera} de Retry y CircuitBreaker
 */
@Slf4j
@Component
public class CarteraPasarelaPago implements PasarelaPago {

    static final String INSTANCIA = "cartera";

    private final CarteraClient cliente;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;
    private final String claveInterna;
    private final JsonMapper jsonMapper;

    public CarteraPasarelaPago(CarteraClient cliente, RetryRegistry retryRegistry,
                               CircuitBreakerRegistry circuitBreakerRegistry,
                               @Value("${seguridad.clave-interna}") String claveInterna, JsonMapper jsonMapper) {
        this.cliente = cliente;
        this.retry = retryRegistry.retry(INSTANCIA);
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(INSTANCIA);
        this.claveInterna = claveInterna;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public UUID cobrar(String jwtComprador, UUID idCompra, BigDecimal monto, LocalDate fechaPago, String descripcion) {
        PagoCarteraRequest cuerpo = new PagoCarteraRequest(
                TipoReferencia.BOLETOS,
                idCompra,
                monto,
                fechaPago,
                descripcion);
        try {
            return ejecutar(() -> cliente.pagar("Bearer " + jwtComprador, idCompra.toString(), cuerpo)).idPago();
        } catch (HttpClientErrorException e) {
            throw rechazo(e);
        }
    }

    @Override
    public Optional<UUID> consultarPago(UUID idCompra) {
        try {
            return Optional.of(ejecutar(() -> cliente.consultar(
                    claveInterna, TipoReferencia.BOLETOS, idCompra)).idPago());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            throw new CarteraNoDisponibleException("la consulta respondió " + e.getStatusCode().value());
        }
    }

    /**
     * Retry por fuera del circuit breaker: con el circuito abierto no se reintenta.
     */
    private <T> T ejecutar(Supplier<T> llamada) {
        try {
            return Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, llamada)).get();
        } catch (ResourceAccessException e) {
            throw new CarteraNoDisponibleException("sin respuesta o tiempo agotado");
        } catch (HttpServerErrorException e) {
            throw new CarteraNoDisponibleException("error " + e.getStatusCode().value());
        } catch (CallNotPermittedException e) {
            throw new CarteraNoDisponibleException("circuito abierto");
        }
    }

    /**
     * Propaga el status y el {@code codigo} de la respuesta de error de Cartera ({@code RespuestaError}).
     */
    private PagoRechazadoException rechazo(HttpClientErrorException e) {
        HttpStatus status = HttpStatus.resolve(e.getStatusCode().value());
        String codigo = "PAGO_RECHAZADO";
        String mensaje = "Cartera rechazó el pago";
        try {
            JsonNode cuerpo = jsonMapper.readTree(e.getResponseBodyAsString());
            if (cuerpo.hasNonNull("codigo")) {
                codigo = cuerpo.get("codigo").asString();
            }
            if (cuerpo.hasNonNull("error")) {
                mensaje = cuerpo.get("error").asString();
            }
        } catch (RuntimeException ilegible) {
            log.warn("Respuesta de error de Cartera ilegible (status {})", e.getStatusCode().value());
        }
        return new PagoRechazadoException(status == null ? HttpStatus.UNPROCESSABLE_CONTENT : status, codigo, mensaje);
    }
}
