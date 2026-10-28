package org.transportsgt.tripservice.clients;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.transportsgt.eventos.contexto.ContextoCorrelacion;
import org.transportsgt.tripservice.config.BoletosProperties;
import org.transportsgt.tripservice.exception.ServicioNoDisponibleException;

import java.time.Duration;
import java.util.UUID;

/**
 * Cuarta llamada síncrona del catálogo: Viajes → Boletos
 * {@code GET /boletos/viajes/{idViaje}/asiento-maximo}. Solo se usa al cambiar el bus de un viaje
 * que ya tiene boletos vendidos. Si Boletos no responde, se rechaza el cambio (falla cerrada).
 */
@Component
public class BoletosClient {

    private static final Logger log = LoggerFactory.getLogger(BoletosClient.class);

    private final RestClient restClient;

    public BoletosClient(RestClient.Builder builder, BoletosProperties propiedades) {
        this.restClient = builder
                .baseUrl(propiedades.url())
                .build();
    }

    /**
     * Mayor asiento RESERVADO o CONFIRMADO del viaje (0 si no hay).
     *
     * @throws ServicioNoDisponibleException si Boletos no responde (falla cerrada)
     */
    public int asientoMaximo(UUID idViaje) {
        try {
            AsientoMaximo cuerpo = restClient.get()
                    .uri("/boletos/viajes/{id}/asiento-maximo", idViaje)
                    .headers(encabezados -> {
                        UUID correlacion = ContextoCorrelacion.actual();
                        if (correlacion != null) {
                            encabezados.set("X-Correlation-Id", correlacion.toString());
                        }
                    })
                    .retrieve()
                    .body(AsientoMaximo.class);
            return cuerpo == null ? 0 : cuerpo.asientoMaximo();
        } catch (RestClientException e) {
            log.warn("Boletos no respondió al consultar asiento máximo del viaje {}: {}", idViaje, e.getMessage());
            throw new ServicioNoDisponibleException(
                    "Boletos no está disponible; no se puede validar la capacidad del bus", e);
        }
    }

    public record AsientoMaximo(UUID idViaje, int asientoMaximo) {
    }
}
