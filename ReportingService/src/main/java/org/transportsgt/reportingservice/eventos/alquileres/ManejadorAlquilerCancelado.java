package org.transportsgt.reportingservice.eventos.alquileres;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosAlquilerCancelado;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionIngresosService;

/**
 * Cola {@code reportes.alquileres.q}, llave {@code alquileres.alquiler.cancelado}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorAlquilerCancelado implements ManejadorEvento<DatosAlquilerCancelado> {

    private final ProyeccionIngresosService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.ALQUILER_CANCELADO;
    }

    @Override
    public void manejar(SobreEvento<DatosAlquilerCancelado> evento) {
        proyeccion.cancelarAlquiler(evento);
    }
}
