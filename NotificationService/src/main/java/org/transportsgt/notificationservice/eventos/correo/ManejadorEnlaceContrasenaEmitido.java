package org.transportsgt.notificationservice.eventos.correo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosEnlaceContrasena;
import org.transportsgt.notificationservice.eventos.ValidacionEventos;
import org.transportsgt.notificationservice.services.enlace.EnlaceContrasenaService;

/**
 * Cola {@code notificaciones.identidad.q}, llave {@code identidad.usuario.enlace-contrasena-emitido}. Envío directo:
 * el payload lleva un token, así que no pasa por la bandeja PENDIENTE. Un fallo del SMTP se relanza para que RabbitMQ
 * reintente y, al agotar, el mensaje vaya a la DLQ.
 */
@Component
@RequiredArgsConstructor
public class ManejadorEnlaceContrasenaEmitido implements ManejadorEvento<DatosEnlaceContrasena> {

    private final EnlaceContrasenaService enlaceContrasena;
    private final ValidacionEventos validacion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.ENLACE_CONTRASENA_EMITIDO;
    }

    @Override
    public void manejar(SobreEvento<DatosEnlaceContrasena> evento) {
        DatosEnlaceContrasena d = validacion.datos(evento);
        validacion.requeridos(evento, "correo", d.correo(), "nombreCompleto", d.nombreCompleto(),
                "proposito", d.proposito(), "token", d.token(), "expiraEn", d.expiraEn());
        validacion.registrar(evento, enlaceContrasena.enviar(evento.idEvento(), d) ? "enlace enviado" : "ya enviado");
    }
}
