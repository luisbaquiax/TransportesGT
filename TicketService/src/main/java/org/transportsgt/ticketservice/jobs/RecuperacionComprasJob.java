package org.transportsgt.ticketservice.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.transportsgt.ticketservice.services.compra.RecuperacionComprasService;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cada minuto revisa las compras {@code RESERVADA} con más de {@code boletos.recuperacion.antiguedad} (plan §4.1).
 * Cada compra se procesa por separado: si una falla, se registra y se sigue con las demás.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecuperacionComprasJob {

    private final RecuperacionComprasService recuperacion;

    @Scheduled(fixedDelayString = "${boletos.recuperacion.intervalo-ms}", initialDelayString = "${boletos.recuperacion.intervalo-ms}")
    public void ejecutar() {
        Map<RecuperacionComprasService.Resultado, Integer> resumen = new EnumMap<>(RecuperacionComprasService.Resultado.class);
        for (UUID idCompra : recuperacion.pendientes()) {
            try {
                resumen.merge(recuperacion.recuperar(idCompra), 1, Integer::sum);
            } catch (RuntimeException e) {
                log.error("No se pudo recuperar la compra {}", idCompra, e);
            }
        }
        if (!resumen.isEmpty()) {
            log.info("Recuperación de compras: {}", resumen);
        }
    }
}
