package org.transportsgt.notificationservice.eventos.correo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerSolicitado;
import org.transportsgt.notificationservice.eventos.ValidacionEventos;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.services.bandeja.Destinatarios;

import java.util.Map;

/**
 * Cola {@code notificaciones.alquileres.q}, llave {@code alquileres.alquiler.solicitado}. Aviso a cada ADMIN_SUCURSAL activo de la sucursal de origen, para que cotice.
 */
@Component
@RequiredArgsConstructor
public class ManejadorAlquilerSolicitado implements ManejadorEvento<DatosAlquilerSolicitado> {

    private final Destinatarios destinatarios;
    private final CorreosDeEvento correos;
    private final ValidacionEventos validacion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.ALQUILER_SOLICITADO;
    }

    @Override
    public void manejar(SobreEvento<DatosAlquilerSolicitado> evento) {
        DatosAlquilerSolicitado d = validacion.datos(evento);
        validacion.requeridos(evento, "idAlquiler", d.idAlquiler(), "idSucursalOrigen", d.idSucursalOrigen());
        int encolados = correos.encolar(evento, TipoNotificacion.ALQUILER_SOLICITADO,
                destinatarios.adminsDeSucursal(d.idSucursalOrigen()), Map.of("datos", d));
        validacion.registrar(evento, encolados + " correo(s) encolado(s)");
    }
}
