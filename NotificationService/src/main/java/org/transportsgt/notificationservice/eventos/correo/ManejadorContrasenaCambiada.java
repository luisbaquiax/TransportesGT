package org.transportsgt.notificationservice.eventos.correo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.identidad.DatosContrasenaCambiada;
import org.transportsgt.notificationservice.eventos.ValidacionEventos;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.services.bandeja.Destinatario;
import org.transportsgt.notificationservice.services.bandeja.Destinatarios;

import java.util.List;
import java.util.Map;

/**
 * Cola {@code notificaciones.identidad.q}, llave {@code identidad.usuario.contrasena-cambiada}. Aviso de seguridad al correo del payload (no depende de {@code contacto}).
 */
@Component
@RequiredArgsConstructor
public class ManejadorContrasenaCambiada implements ManejadorEvento<DatosContrasenaCambiada> {

    private final Destinatarios destinatarios;
    private final CorreosDeEvento correos;
    private final ValidacionEventos validacion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.CONTRASENA_CAMBIADA;
    }

    @Override
    public void manejar(SobreEvento<DatosContrasenaCambiada> evento) {
        DatosContrasenaCambiada d = validacion.datos(evento);
        validacion.requeridos(evento, "correo", d.correo(), "nombreCompleto", d.nombreCompleto(), "motivo", d.motivo());
        Destinatario destinatario = new Destinatario(d.idUsuario(), d.correo(), d.nombreCompleto());
        int encolados = correos.encolar(evento, TipoNotificacion.CONTRASENA_CAMBIADA, List.of(destinatario),
                Map.of("datos", d));
        validacion.registrar(evento, encolados + " correo(s) encolado(s)");
    }
}
