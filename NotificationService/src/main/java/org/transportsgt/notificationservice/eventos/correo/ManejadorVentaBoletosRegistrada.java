package org.transportsgt.notificationservice.eventos.correo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.boletos.DatosVentaBoletos;
import org.transportsgt.notificationservice.eventos.ValidacionEventos;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.services.bandeja.Destinatarios;

import java.util.List;
import java.util.Map;

/**
 * Cola {@code notificaciones.boletos.q}, llave {@code boletos.venta.registrada}. Confirmación al comprador; una venta de mostrador sin cuenta no genera correo.
 */
@Component
@RequiredArgsConstructor
public class ManejadorVentaBoletosRegistrada implements ManejadorEvento<DatosVentaBoletos> {

    private final Destinatarios destinatarios;
    private final CorreosDeEvento correos;
    private final ValidacionEventos validacion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.VENTA_BOLETOS_REGISTRADA;
    }

    @Override
    public void manejar(SobreEvento<DatosVentaBoletos> evento) {
        DatosVentaBoletos d = validacion.datos(evento);
        validacion.requeridos(evento, "idCompra", d.idCompra(), "asientos", d.asientos(), "montoTotal", d.montoTotal());
        if (d.idUsuarioComprador() == null) {
            validacion.registrar(evento, "venta sin cuenta: sin correo");
            return;
        }
        int encolados = correos.encolar(evento, TipoNotificacion.COMPRA_BOLETOS,
                List.of(destinatarios.usuario(d.idUsuarioComprador())), Map.of("datos", d));
        validacion.registrar(evento, encolados + " correo(s) encolado(s)");
    }
}
