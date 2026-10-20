package org.transportsgt.notificationservice.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.transportsgt.notificationservice.services.licencia.AvisosLicenciaService;

/**
 * Diario a las 7:00 en hora de Guatemala. Encola los correos; el job de envío los manda en los segundos siguientes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AvisosLicenciaJob {

    private final AvisosLicenciaService avisos;

    @Scheduled(cron = "${notificaciones.licencias.cron}", zone = "America/Guatemala")
    public void ejecutar() {
        log.info("Avisos de licencia: {}", avisos.procesar());
    }
}
