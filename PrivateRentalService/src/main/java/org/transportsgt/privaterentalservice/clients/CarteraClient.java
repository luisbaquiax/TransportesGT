package org.transportsgt.privaterentalservice.clients;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.transportsgt.eventos.ContextoCorrelacion;
import org.transportsgt.eventos.payload.TipoReferencia;
import org.transportsgt.privaterentalservice.exception.ServicioNoDisponibleException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Cliente de Cartera para la saga de pago (plan §4.2): se reenvía el JWT del usuario
 * y {@code Clave-Idempotencia} = idAlquiler. Si Cartera no responde, la operación se rechaza
 * con 503 (falla cerrada): nunca se confirma el alquiler sin pago registrado.
 */
@Component
public class CarteraClient {

    private static final Logger log = LoggerFactory.getLogger(CarteraClient.class);

    private final RestClient restClient;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    private final String claveInterna;

    public CarteraClient(RestClient.Builder builder,
                         @Value("${cartera.url:http://localhost:8085/v1/customers}") String url,
                         @Value("${alquileres.clave-interna:}") String claveInterna) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = builder.baseUrl(url).requestFactory(factory).build();
        this.claveInterna = claveInterna;
        this.retry = Retry.of("cartera", RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(300))
                .retryExceptions(ServicioNoDisponibleException.class)
                .build());
        this.circuitBreaker = CircuitBreaker.of("cartera", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(15))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(ServicioNoDisponibleException.class)
                .build());
    }

    /**
     * Cobra el alquiler en la cartera del usuario.
     *
     * @param jwtUsuario token Bearer del cliente (se reenvía tal cual)
     * @return idPago del movimiento creado o ya existente
     * @throws ServicioNoDisponibleException si Cartera no responde
     */
    public UUID pagar(String jwtUsuario, UUID idAlquiler, BigDecimal monto, LocalDate fechaPago, String descripcion) {
        java.util.function.Supplier<UUID> llamada = retry.decorateSupplier(
                () -> ejecutar(jwtUsuario, idAlquiler, monto, fechaPago, descripcion));
        try {
            return circuitBreaker.executeSupplier(llamada);
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker de Cartera abierto: no se pudo cobrar el alquiler {}", idAlquiler);
            throw new ServicioNoDisponibleException("Cartera", e);
        }
    }

    /**
     * Consulta si ya existe el pago de un alquiler (plan §4.2: recuperación de la saga).
     * Se usa con la clave interna del job, sin token de usuario.
     *
     * @return el pago si existe; vacío si Cartera responde 404
     * @throws ServicioNoDisponibleException si Cartera no responde
     */
    public Optional<PagoEncontrado> consultarPago(UUID idAlquiler) {
        try {
            Map<?, ?> respuesta = restClient.get()
                    .uri("/cartera/pagos?tipoReferencia={tipo}&idReferencia={id}",
                            TipoReferencia.ALQUILER.name(), idAlquiler)
                    .header("X-Clave-Interna", claveInterna)
                    .headers(h -> {
                        UUID correlacion = ContextoCorrelacion.actual();
                        if (correlacion != null) {
                            h.set("X-Correlation-Id", correlacion.toString());
                        }
                    })
                    .retrieve()
                    .body(Map.class);
            if (respuesta == null || respuesta.get("idPago") == null) {
                return Optional.empty();
            }
            return Optional.of(new PagoEncontrado(
                    UUID.fromString(respuesta.get("idPago").toString()),
                    respuesta.get("fechaPago") == null ? null : LocalDate.parse(respuesta.get("fechaPago").toString())));
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (RestClientException e) {
            log.warn("Cartera no respondió al consultar el pago del alquiler {}: {}", idAlquiler, e.getMessage());
            throw new ServicioNoDisponibleException("Cartera", e);
        }
    }

    /** Resultado de {@link #consultarPago}. */
    public record PagoEncontrado(UUID idPago, LocalDate fechaPago) {
    }

    private UUID ejecutar(String jwtUsuario, UUID idAlquiler, BigDecimal monto, LocalDate fechaPago, String descripcion) {
        try {
            Map<String, Object> cuerpo = Map.of(
                    "tipoReferencia", TipoReferencia.ALQUILER.name(),
                    "idReferencia", idAlquiler.toString(),
                    "monto", monto.toPlainString(),
                    "fechaPago", fechaPago.toString(),
                    "descripcion", descripcion == null ? "Alquiler privado" : descripcion);
            Map<?, ?> respuesta = restClient.post()
                    .uri("/cartera/pagos")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", jwtUsuario)
                    .header("Clave-Idempotencia", idAlquiler.toString())
                    .headers(h -> {
                        UUID correlacion = ContextoCorrelacion.actual();
                        if (correlacion != null) {
                            h.set("X-Correlation-Id", correlacion.toString());
                        }
                    })
                    .body(cuerpo)
                    .retrieve()
                    .body(Map.class);
            Object idPago = respuesta == null ? null : respuesta.get("idPago");
            return idPago == null ? null : UUID.fromString(idPago.toString());
        } catch (RestClientException e) {
            log.warn("Cartera no respondió para el alquiler {}: {}", idAlquiler, e.getMessage());
            throw new ServicioNoDisponibleException("Cartera", e);
        }
    }
}
