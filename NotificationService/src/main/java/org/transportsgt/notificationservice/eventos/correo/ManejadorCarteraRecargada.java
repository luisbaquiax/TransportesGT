package org.transportsgt.notificationservice.eventos.correo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosCarteraRecargada;
import org.transportsgt.notificationservice.eventos.ValidacionEventos;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.services.bandeja.Destinatarios;

import java.util.List;
import java.util.Map;

/**
 * Cola {@code notificaciones.cartera.q}, llave {@code cartera.cartera.recargada}. Aviso al dueño de la cartera, con el saldo posterior.
 */
@Component
@RequiredArgsConstructor
public class ManejadorCarteraRecargada implements ManejadorEvento<DatosCarteraRecargada> {

    private final Destinatarios destinatarios;
    private final CorreosDeEvento correos;
    private final ValidacionEventos validacion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.CARTERA_RECARGADA;
    }

    @Override
    public void manejar(SobreEvento<DatosCarteraRecargada> evento) {
        DatosCarteraRecargada d = validacion.datos(evento);
        validacion.requeridos(evento, "idUsuario", d.idUsuario(), "monto", d.monto(), "saldoPosterior", d.saldoPosterior());
        int encolados = correos.encolar(evento, TipoNotificacion.CARTERA_RECARGADA,
                List.of(destinatarios.usuario(d.idUsuario())), Map.of("datos", d));
        validacion.registrar(evento, encolados + " correo(s) encolado(s)");
    }
}
