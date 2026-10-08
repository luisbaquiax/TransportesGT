package org.transportsgt.identityservice.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.identityservice.config.IdentidadProperties;
import org.transportsgt.identityservice.repositories.TokenContrasenaRepository;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Diario: borra los tokens de contraseña vencidos hace más de 7 días.
 * La limpieza del outbox (publicados con más de 24 h) la hace la librería de eventos
 * ({@code PublicadorOutbox.limpiarPublicados}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LimpiezaTokensJob {

    private final TokenContrasenaRepository tokenRepository;
    private final IdentidadProperties propiedades;
    private final Clock clock;

    @Scheduled(cron = "${identidad.jobs.limpieza-tokens-cron:0 0 3 * * *}")
    @Transactional
    public int limpiarTokensVencidos() {
        OffsetDateTime limite = OffsetDateTime.now(clock).minus(propiedades.retencionTokens());
        int borrados = tokenRepository.eliminarVencidosAntesDe(limite);
        if (borrados > 0) {
            log.info("Limpieza de tokens: {} token(s) vencidos antes de {} eliminados", borrados, limite);
        }
        return borrados;
    }
}
