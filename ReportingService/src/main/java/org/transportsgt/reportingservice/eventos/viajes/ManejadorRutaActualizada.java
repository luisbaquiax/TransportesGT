package org.transportsgt.reportingservice.eventos.viajes;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosRuta;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionViajesService;

/**
 * Cola {@code reportes.viajes.q}, llave {@code viajes.ruta.actualizada}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorRutaActualizada implements ManejadorEvento<DatosRuta> {

    private final ProyeccionViajesService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.RUTA_ACTUALIZADA;
    }

    @Override
    public void manejar(SobreEvento<DatosRuta> evento) {
        proyeccion.aplicarRuta(evento);
    }
}
