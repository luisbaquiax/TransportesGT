package org.transportsgt.notificationservice.services.enlace.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.DatosEnlaceContrasena;
import org.transportsgt.eventos.payload.PropositoContrasena;
import org.transportsgt.notificationservice.correo.CorreoRenderizado;
import org.transportsgt.notificationservice.correo.EnviadorCorreo;
import org.transportsgt.notificationservice.correo.MensajeCorreo;
import org.transportsgt.notificationservice.correo.RenderizadorCorreo;
import org.transportsgt.notificationservice.models.EstadoNotificacion;
import org.transportsgt.notificationservice.models.Notificacion;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.repositories.NotificacionRepository;
import org.transportsgt.notificationservice.services.bandeja.ClaveDedup;
import org.transportsgt.notificationservice.services.bandeja.Destinatario;
import org.transportsgt.notificationservice.services.enlace.EnlaceContrasenaService;
import org.transportsgt.notificationservice.utils.Fechas;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnlaceContrasenaServiceImpl implements EnlaceContrasenaService {

    static final String CUERPO_NO_ALMACENADO = "[contenido sensible no almacenado]";

    private final NotificacionRepository notificacionRepository;
    private final ClaveDedup claveDedup;
    private final ConstructorEnlaces enlaces;
    private final RenderizadorCorreo renderizador;
    private final EnviadorCorreo enviador;
    private final Fechas fechas;

    @Override
    @Transactional
    public boolean enviar(UUID idEvento, DatosEnlaceContrasena datos) {
        String clave = claveDedup.evento(idEvento, datos.correo());
        if (notificacionRepository.findByClaveDedup(clave)
                .filter(n -> n.getEstado() == EstadoNotificacion.ENVIADA).isPresent()) {
            return false;
        }
        Destinatario destinatario = new Destinatario(datos.idUsuario(), datos.correo(), datos.nombreCompleto());
        CorreoRenderizado correo = renderizador.renderizar(TipoNotificacion.ENLACE_CONTRASENA, Map.of(
                "destinatario", destinatario,
                "activacion", datos.proposito() == PropositoContrasena.ACTIVACION,
                "enlace", enlaces.construir(datos.proposito(), datos.token()),
                "expiraEn", datos.expiraEn()));
        enviador.enviar(new MensajeCorreo(datos.correo(), correo.asunto(), correo.html()));

        notificacionRepository.save(Notificacion.builder()
                .tipo(TipoNotificacion.ENLACE_CONTRASENA)
                .correoDestino(datos.correo())
                .idUsuario(datos.idUsuario())
                .asunto(correo.asunto())
                .cuerpo(CUERPO_NO_ALMACENADO)
                .estado(EstadoNotificacion.ENVIADA)
                .intentos((short) 1)
                .claveDedup(clave)
                .idEventoOrigen(idEvento)
                .enviadaEn(fechas.ahora())
                .build());
        return true;
    }
}
