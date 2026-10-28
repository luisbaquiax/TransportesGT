package org.transportsgt.privaterentalservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.viajes.DatosLlegada;
import org.transportsgt.privaterentalservice.services.proyeccion.SeguimientoViajeService;

/** LlegadaRegistrada de un viaje de alquiler: cierra el alquiler (CONFIRMADO → FINALIZADO). */
@Component
@RequiredArgsConstructor
public class ManejadorLlegadaRegistrada implements ManejadorEvento<DatosLlegada> {

    private final SeguimientoViajeService servicio;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.LLEGADA_REGISTRADA;
    }

    @Override
    public void manejar(SobreEvento<DatosLlegada> evento) {
        servicio.finalizarPorLlegada(evento.datos());
    }
}
