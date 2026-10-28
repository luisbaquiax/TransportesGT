package org.transportsgt.notificationservice.eventos.correo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.services.bandeja.BandejaSalida;
import org.transportsgt.notificationservice.services.bandeja.ClaveDedup;
import org.transportsgt.notificationservice.services.bandeja.Destinatario;
import org.transportsgt.notificationservice.services.bandeja.SolicitudCorreo;

import java.util.Collection;
import java.util.Map;

/**
 * Encola los correos de un evento
 */
@Component
@RequiredArgsConstructor
class CorreosDeEvento {

    private final BandejaSalida bandeja;
    private final ClaveDedup claveDedup;

    /**
     * @return cuántos correos se encolaron (0 si el evento ya se había procesado)
     */
    int encolar(SobreEvento<?> evento, TipoNotificacion tipo, Collection<Destinatario> destinatarios,
                Map<String, Object> variables) {
        int encolados = 0;
        for (Destinatario destinatario : destinatarios) {
            SolicitudCorreo solicitud = new SolicitudCorreo(tipo, destinatario, variables,
                    claveDedup.evento(evento.idEvento(), destinatario.correo()), evento.idEvento());
            if (bandeja.encolar(solicitud)) {
                encolados++;
            }
        }
        return encolados;
    }
}
