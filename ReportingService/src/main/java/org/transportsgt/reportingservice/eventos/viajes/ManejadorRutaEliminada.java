package org.transportsgt.reportingservice.eventos.viajes;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosRutaEliminada;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionViajesService;

/**
 * Cola {@code reportes.viajes.q}, llave {@code viajes.ruta.eliminada}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorRutaEliminada implements ManejadorEvento<DatosRutaEliminada> {

    private final ProyeccionViajesService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.RUTA_ELIMINADA;
    }

    @Override
    public void manejar(SobreEvento<DatosRutaEliminada> evento) {
        proyeccion.eliminarRuta(evento);
    }
}
