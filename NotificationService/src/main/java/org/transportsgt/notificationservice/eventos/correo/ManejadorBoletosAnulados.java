package org.transportsgt.notificationservice.eventos.correo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.boletos.Canal;
import org.transportsgt.eventos.payload.boletos.DatosBoletosAnulados;
import org.transportsgt.notificationservice.eventos.ValidacionEventos;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.services.bandeja.Destinatarios;

import java.util.List;
import java.util.Map;

/**
 * Cola {@code notificaciones.boletos.q}, llave {@code boletos.venta.anulada}. Aviso al comprador; indica el reembolso a la cartera si la compra fue en línea y tenía pago.
 */
@Component
@RequiredArgsConstructor
public class ManejadorBoletosAnulados implements ManejadorEvento<DatosBoletosAnulados> {

    private final Destinatarios destinatarios;
    private final CorreosDeEvento correos;
    private final ValidacionEventos validacion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.BOLETOS_ANULADOS;
    }

    @Override
    public void manejar(SobreEvento<DatosBoletosAnulados> evento) {
        DatosBoletosAnulados d = validacion.datos(evento);
        validacion.requeridos(evento, "idCompra", d.idCompra(), "canal", d.canal(), "montoTotal", d.montoTotal());
        if (d.idUsuarioComprador() == null) {
            validacion.registrar(evento, "compra sin cuenta: sin correo");
            return;
        }
        // Cartera solo reembolsa compras en línea pagadas con la cartera
        boolean reembolso = d.canal() == Canal.EN_LINEA && d.idPago() != null;
        int encolados = correos.encolar(evento, TipoNotificacion.BOLETOS_ANULADOS,
                List.of(destinatarios.usuario(d.idUsuarioComprador())), Map.of("datos", d, "reembolso", reembolso));
        validacion.registrar(evento, encolados + " correo(s) encolado(s)");
    }
}
