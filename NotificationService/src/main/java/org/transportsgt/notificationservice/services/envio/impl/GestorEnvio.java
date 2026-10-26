package org.transportsgt.notificationservice.services.envio.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.notificationservice.correo.EnviadorCorreo;
import org.transportsgt.notificationservice.correo.EnvioCorreoException;
import org.transportsgt.notificationservice.correo.MensajeCorreo;
import org.transportsgt.notificationservice.models.EstadoNotificacion;
import org.transportsgt.notificationservice.models.Notificacion;
import org.transportsgt.notificationservice.repositories.NotificacionRepository;
import org.transportsgt.notificationservice.utils.Fechas;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Un correo por transacción. Bean aparte de {@link EnvioCorreosServiceImpl} para que el proxy de
 * {@code @Transactional} aplique en cada llamada. La fila queda bloqueada ({@code SKIP LOCKED}) mientras se envía:
 * otra instancia del job no la toma.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class GestorEnvio {

    enum Resultado { ENVIADA, REPROGRAMADA, FALLIDA }

    private final NotificacionRepository notificacionRepository;
    private final EnviadorCorreo enviador;
    private final PoliticaReintentos politica;
    private final Fechas fechas;

    /**
     * @return vacío si no quedan correos listos
     */
    @Transactional
    public Optional<Resultado> enviarSiguiente() {
        OffsetDateTime ahora = fechas.ahora();
        Optional<Notificacion> siguiente = notificacionRepository.bloquearSiguientePendiente(ahora);
        if (siguiente.isEmpty()) {
            return Optional.empty();
        }
        Notificacion notificacion = siguiente.get();
        int intentos = notificacion.getIntentos() + 1;
        notificacion.setIntentos((short) intentos);
        try {
            enviador.enviar(new MensajeCorreo(notificacion.getCorreoDestino(), notificacion.getAsunto(),
                    notificacion.getCuerpo()));
            notificacion.setEstado(EstadoNotificacion.ENVIADA);
            notificacion.setEnviadaEn(ahora);
            notificacion.setUltimoError(null);
            return Optional.of(Resultado.ENVIADA);
        } catch (EnvioCorreoException e) {
            notificacion.setUltimoError(politica.recortar(e.getMessage()));
            if (politica.agotado(intentos)) {
                notificacion.setEstado(EstadoNotificacion.FALLIDA);
                log.warn("Notificación {} ({}) FALLIDA tras {} intentos", notificacion.getId(), notificacion.getTipo(),
                        intentos);
                return Optional.of(Resultado.FALLIDA);
            }
            notificacion.setProximoIntentoEn(ahora.plus(politica.espera(intentos)));
            return Optional.of(Resultado.REPROGRAMADA);
        }
    }
}
