package org.transportsgt.notificationservice.eventos.correo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.cartera.DatosCarteraReembolsada;
import org.transportsgt.eventos.payload.costos.TipoReferencia;
import org.transportsgt.notificationservice.eventos.ValidacionEventos;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.services.bandeja.Destinatarios;

import java.util.List;
import java.util.Map;

/**
 * Cola {@code notificaciones.cartera.q}, llave {@code cartera.cartera.reembolsada}. Aviso al dueño de la cartera, con el saldo posterior.
 */
@Component
@RequiredArgsConstructor
public class ManejadorCarteraReembolsada implements ManejadorEvento<DatosCarteraReembolsada> {

    private final Destinatarios destinatarios;
    private final CorreosDeEvento correos;
    private final ValidacionEventos validacion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.CARTERA_REEMBOLSADA;
    }

    @Override
    public void manejar(SobreEvento<DatosCarteraReembolsada> evento) {
        DatosCarteraReembolsada d = validacion.datos(evento);
        validacion.requeridos(evento, "idUsuario", d.idUsuario(), "monto", d.monto(), "saldoPosterior", d.saldoPosterior());
        String referencia = d.tipoReferencia() == TipoReferencia.ALQUILER
                ? "la cancelación de tu alquiler" : "la anulación de tu compra de boletos";
        int encolados = correos.encolar(evento, TipoNotificacion.CARTERA_REEMBOLSADA,
                List.of(destinatarios.usuario(d.idUsuario())), Map.of("datos", d, "referencia", referencia));
        validacion.registrar(evento, encolados + " correo(s) encolado(s)");
    }
}
