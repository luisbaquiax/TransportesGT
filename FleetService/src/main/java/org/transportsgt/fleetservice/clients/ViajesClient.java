package org.transportsgt.fleetservice.clients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.transportsgt.eventos.ContextoCorrelacion;
import org.transportsgt.fleetservice.config.ViajesProperties;
import org.transportsgt.fleetservice.exception.ViajesNoDisponibleException;

import java.time.Duration;
import java.util.UUID;

/**
 * Cliente de Viajes (plan §2.6): RestClient con timeout de 3 s, 2 reintentos con espera creciente
 * y circuit breaker. Solo se usan operaciones idempotentes (GET). Si Viajes no responde, la
 * operación se rechaza con 503 (falla cerrada): nunca se asume que no hay viajes activos.
 */
@Component
public class ViajesClient {

    private static final Logger log = LoggerFactory.getLogger(ViajesClient.class);

    private final RestClient restClient;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public ViajesClient(RestClient.Builder builder, ViajesProperties propiedades) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(3));
        this.restClient = builder
                .baseUrl(propiedades.url())
                .requestFactory(factory)
                .build();
        this.retry = Retry.of("viajes", RetryConfig.custom()
                .maxAttempts(3)                                   // 1 + 2 reintentos
                .waitDuration(Duration.ofMillis(200))
                .intervalFunction(IntervalFunction
                        .ofExponentialBackoff(Duration.ofMillis(200), 2))
                .retryExceptions(ViajesNoDisponibleException.class)
                .build());
        this.circuitBreaker = CircuitBreaker.of("viajes", CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(ViajesNoDisponibleException.class)
                .build());
    }

    /**
     * ¿El bus tiene viajes PROGRAMADO o EN_TRANSITO? Lo usa Flota antes de desactivar un bus.
     *
     * @throws ViajesNoDisponibleException si Viajes no responde o el circuit breaker está abierto
     */
    public boolean hayViajesActivos(UUID idBus) {
        java.util.function.Supplier<Boolean> llamada = retry.decorateSupplier(() -> consulta(idBus));
        try {
            return circuitBreaker.executeSupplier(llamada);
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker de Viajes abierto: no se pudo consultar el bus {}", idBus);
            throw new ViajesNoDisponibleException(e);
        }
    }

    private boolean consulta(UUID idBus) {
        try {
            Object[] cuerpo = restClient.get()
                    .uri("/viajes/activos?idBus={id}", idBus)
                    .headers(encabezados -> {
                        UUID correlacion = ContextoCorrelacion.actual();
                        if (correlacion != null) {
                            encabezados.set("X-Correlation-Id", correlacion.toString());
                        }
                    })
                    .retrieve()
                    .body(Object[].class);
            return cuerpo != null && cuerpo.length > 0;
        } catch (RestClientException e) {
            log.warn("Viajes no respondió para el bus {}: {}", idBus, e.getMessage());
            throw new ViajesNoDisponibleException(e);
        }
    }
}
