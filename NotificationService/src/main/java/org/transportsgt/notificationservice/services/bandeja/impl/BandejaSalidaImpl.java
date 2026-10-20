package org.transportsgt.notificationservice.services.bandeja.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.notificationservice.correo.CorreoRenderizado;
import org.transportsgt.notificationservice.correo.RenderizadorCorreo;
import org.transportsgt.notificationservice.repositories.NotificacionRepository;
import org.transportsgt.notificationservice.services.bandeja.BandejaSalida;
import org.transportsgt.notificationservice.services.bandeja.SolicitudCorreo;
import org.transportsgt.notificationservice.utils.Fechas;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BandejaSalidaImpl implements BandejaSalida {

    private final RenderizadorCorreo renderizador;
    private final NotificacionRepository notificacionRepository;
    private final Fechas fechas;

    @Override
    @Transactional
    public boolean encolar(SolicitudCorreo solicitud) {
        Map<String, Object> variables = new HashMap<>(solicitud.variables());
        variables.put("destinatario", solicitud.destinatario());
        CorreoRenderizado correo = renderizador.renderizar(solicitud.tipo(), variables);
        return notificacionRepository.insertarPendiente(solicitud.tipo().name(), solicitud.destinatario().correo(),
                solicitud.destinatario().idUsuario(), correo.asunto(), correo.html(), solicitud.claveDedup(),
                solicitud.idEventoOrigen(), fechas.ahora()) == 1;
    }
}
