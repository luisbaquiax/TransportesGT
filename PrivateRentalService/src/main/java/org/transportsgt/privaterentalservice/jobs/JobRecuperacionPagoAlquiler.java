package org.transportsgt.privaterentalservice.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.boletos.Canal;
import org.transportsgt.privaterentalservice.clients.CarteraClient;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.repositories.AlquilerRepository;
import org.transportsgt.privaterentalservice.services.comun.CalendarioGuatemala;
import org.transportsgt.privaterentalservice.services.pago.ConfirmadorAlquiler;
import org.transportsgt.privaterentalservice.dto.PagoRealizado;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Job de recuperación de la saga de pago (plan §4.2): si Cartera no respondió o Alquileres cayó
 * antes de confirmar, el alquiler queda COTIZADO sin {@code idPago}. Este job consulta
 * {@code GET /cartera/pagos?tipoReferencia=ALQUILER&idReferencia=} y confirma el alquiler
 * si el pago ya existe en Cartera. No hace nada si Cartera no responde (reintenta en el
 * siguiente ciclo). Corre cada minuto, como el de Boletos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobRecuperacionPagoAlquiler {

    /** Umbral para considerar "en duda" un alquiler cotizado sin pagar (como Boletos: 2 minutos). */
    private static final java.time.Duration UMBRAL_EN_DUDA = java.time.Duration.ofMinutes(2);

    private final AlquilerRepository alquilerRepository;
    private final CarteraClient carteraClient;
    private final ConfirmadorAlquiler confirmador;
    private final CalendarioGuatemala calendario;

    @Scheduled(fixedDelayString = "${alquileres.recuperacion.intervalo-ms:60000}")
    @Transactional
    public void recuperarPagosEnDuda() {
        OffsetDateTime limite = calendario.ahora().minus(UMBRAL_EN_DUDA);
        List<Alquiler> candidatos = alquilerRepository.findPagosEnDuda(
                EstadoAlquiler.COTIZADO, Canal.EN_LINEA, limite);
        if (candidatos.isEmpty()) {
            return;
        }
        log.info("Job recuperación: {} alquiler(es) cotizado(s) en línea sin pago", candidatos.size());
        for (Alquiler a : candidatos) {
            try {
                carteraClient.consultarPago(a.getId()).ifPresent(pago -> {
                    LocalDate fecha = pago.fechaPago() != null ? pago.fechaPago() : calendario.hoy();
                    confirmador.confirmar(a, new PagoRealizado(pago.idPago(), fecha));
                    log.info("Job recuperación: alquiler {} confirmado con el pago {} ya existente en Cartera",
                            a.getId(), pago.idPago());
                });
            } catch (Exception e) {
                // Cartera no responde: no hace nada; se reintenta en el siguiente ciclo.
                log.warn("No se pudo consultar el pago del alquiler {}: {}", a.getId(), e.getMessage());
            }
        }
    }
}
