package org.transportsgt.identityservice.jobs;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.transportsgt.identityservice.services.contrasena.EnlacesContrasenaService;

/**
 * Diario: borra los tokens de contraseña vencidos hace más de 7 días.
 * La limpieza del outbox (publicados con más de 24 h) la hace la librería de eventos
 * ({@code PublicadorOutbox.limpiarPublicados}).
 */
@Component
@RequiredArgsConstructor
public class LimpiezaTokensJob {

    private final EnlacesContrasenaService enlaces;

    @Scheduled(cron = "${identidad.jobs.limpieza-tokens-cron:0 0 3 * * *}")
    public int limpiarTokensVencidos() {
        return enlaces.limpiarVencidos();
    }
}
