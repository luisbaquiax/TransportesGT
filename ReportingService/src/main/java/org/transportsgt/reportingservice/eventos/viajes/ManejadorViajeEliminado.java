package org.transportsgt.reportingservice.eventos.viajes;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.viajes.DatosViajeEliminado;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionViajesService;

/**
 * Cola {@code reportes.viajes.q}, llave {@code viajes.viaje.eliminado}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorViajeEliminado implements ManejadorEvento<DatosViajeEliminado> {

    private final ProyeccionViajesService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.VIAJE_ELIMINADO;
    }

    @Override
    public void manejar(SobreEvento<DatosViajeEliminado> evento) {
        proyeccion.eliminarViaje(evento);
    }
}
