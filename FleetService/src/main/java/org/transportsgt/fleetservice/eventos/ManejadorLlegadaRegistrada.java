package org.transportsgt.fleetservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosLlegada;
import org.transportsgt.fleetservice.services.proyeccion.KilometrajeBusService;

/** Llegada de un viaje: kilometraje final y el bus vuelve a DISPONIBLE si seguía EN_TRANSITO. */
@Component
@RequiredArgsConstructor
public class ManejadorLlegadaRegistrada implements ManejadorEvento<DatosLlegada> {

    private final KilometrajeBusService servicio;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.LLEGADA_REGISTRADA;
    }

    @Override
    public void manejar(SobreEvento<DatosLlegada> evento) {
        servicio.registrarLlegada(evento.datos());
    }
}
