package org.transportsgt.notificationservice.eventos.correo;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosAlquilerConfirmado;
import org.transportsgt.notificationservice.eventos.ValidacionEventos;
import org.transportsgt.notificationservice.models.TipoNotificacion;
import org.transportsgt.notificationservice.services.bandeja.Destinatarios;

import java.util.List;
import java.util.Map;

/**
 * Cola {@code notificaciones.alquileres.q}, llave {@code alquileres.alquiler.confirmado}. Al cliente (si tiene cuenta) y a cada ADMIN_SUCURSAL activo de la sucursal, que debe asignar bus y chofer.
 */
@Component
@RequiredArgsConstructor
public class ManejadorAlquilerConfirmado implements ManejadorEvento<DatosAlquilerConfirmado> {

    private final Destinatarios destinatarios;
    private final CorreosDeEvento correos;
    private final ValidacionEventos validacion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.ALQUILER_CONFIRMADO;
    }

    @Override
    public void manejar(SobreEvento<DatosAlquilerConfirmado> evento) {
        DatosAlquilerConfirmado d = validacion.datos(evento);
        validacion.requeridos(evento, "idAlquiler", d.idAlquiler(), "idSucursalOrigen", d.idSucursalOrigen());
        int encolados = 0;
        if (d.idUsuarioCliente() != null) {
            encolados += correos.encolar(evento, TipoNotificacion.ALQUILER_CONFIRMADO,
                    List.of(destinatarios.usuario(d.idUsuarioCliente())), Map.of("datos", d, "paraAdmin", false));
        }
        encolados += correos.encolar(evento, TipoNotificacion.ALQUILER_CONFIRMADO,
                destinatarios.adminsDeSucursal(d.idSucursalOrigen()), Map.of("datos", d, "paraAdmin", true));
        validacion.registrar(evento, encolados + " correo(s) encolado(s)");
    }
}
