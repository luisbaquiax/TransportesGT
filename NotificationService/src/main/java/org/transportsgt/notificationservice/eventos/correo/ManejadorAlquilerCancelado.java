package org.transportsgt.notificationservice.eventos.correo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerCancelado;
import org.transportsgt.notificationservice.eventos.ValidacionEventos;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.services.bandeja.Destinatarios;

import java.util.List;
import java.util.Map;

/**
 * Cola {@code notificaciones.alquileres.q}, llave {@code alquileres.alquiler.cancelado}. Aviso al cliente (si tiene cuenta); indica el reembolso a la cartera si se pagó con ella.
 */
@Component
@RequiredArgsConstructor
public class ManejadorAlquilerCancelado implements ManejadorEvento<DatosAlquilerCancelado> {

    private final Destinatarios destinatarios;
    private final CorreosDeEvento correos;
    private final ValidacionEventos validacion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.ALQUILER_CANCELADO;
    }

    @Override
    public void manejar(SobreEvento<DatosAlquilerCancelado> evento) {
        DatosAlquilerCancelado d = validacion.datos(evento);
        validacion.requeridos(evento, "idAlquiler", d.idAlquiler());
        if (d.idUsuarioCliente() == null) {
            validacion.registrar(evento, "cliente sin cuenta: sin correo");
            return;
        }
        int encolados = correos.encolar(evento, TipoNotificacion.ALQUILER_CANCELADO,
                List.of(destinatarios.usuario(d.idUsuarioCliente())), Map.of("datos", d, "reembolso", d.idPago() != null));
        validacion.registrar(evento, encolados + " correo(s) encolado(s)");
    }
}
