package org.transportsgt.notificationservice.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.transportsgt.notificationservice.services.envio.EnvioCorreosService;
import org.transportsgt.notificationservice.services.envio.ResumenEnvio;

/**
 * Cada 30 s envía los correos PENDIENTE. {@code fixedDelay}: una corrida no empieza hasta que termina la anterior.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnvioCorreosJob {

    private final EnvioCorreosService envio;

    @Scheduled(fixedDelayString = "${notificaciones.envio.intervalo-ms}",
            initialDelayString = "${notificaciones.envio.intervalo-ms}")
    public void ejecutar() {
        ResumenEnvio resumen = envio.procesar();
        if (resumen.enviadas() + resumen.reprogramadas() + resumen.fallidas() > 0) {
            log.info("Envío de correos: {}", resumen);
        }
    }
}
