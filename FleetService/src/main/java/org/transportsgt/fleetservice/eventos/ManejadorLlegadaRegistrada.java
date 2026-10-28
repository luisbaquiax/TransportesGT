package org.transportsgt.fleetservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.viajes.DatosLlegada;
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
