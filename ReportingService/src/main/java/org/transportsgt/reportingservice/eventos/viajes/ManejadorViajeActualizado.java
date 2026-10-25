package org.transportsgt.reportingservice.eventos.viajes;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosViaje;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionViajesService;

/**
 * Cola {@code reportes.viajes.q}, llave {@code viajes.viaje.actualizado}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorViajeActualizado implements ManejadorEvento<DatosViaje> {

    private final ProyeccionViajesService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.VIAJE_ACTUALIZADO;
    }

    @Override
    public void manejar(SobreEvento<DatosViaje> evento) {
        proyeccion.aplicarViaje(evento);
    }
}
