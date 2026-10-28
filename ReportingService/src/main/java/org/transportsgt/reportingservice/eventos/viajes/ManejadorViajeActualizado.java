package org.transportsgt.reportingservice.eventos.viajes;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.viajes.DatosViaje;
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
