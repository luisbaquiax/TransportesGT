package org.transportsgt.privaterentalservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.viajes.DatosViaje;
import org.transportsgt.privaterentalservice.services.proyeccion.SeguimientoViajeService;

/** ViajeActualizado: refleja la asignación de bus y chofer en el alquiler (plan §3.6). */
@Component
@RequiredArgsConstructor
public class ManejadorViajeActualizado implements ManejadorEvento<DatosViaje> {

    private final SeguimientoViajeService servicio;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.VIAJE_ACTUALIZADO;
    }

    @Override
    public void manejar(SobreEvento<DatosViaje> evento) {
        servicio.actualizarRecursos(evento.datos(), evento.versionAgregado());
    }
}
