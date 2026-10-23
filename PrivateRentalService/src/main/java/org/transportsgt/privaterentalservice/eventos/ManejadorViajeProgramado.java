package org.transportsgt.privaterentalservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosViaje;
import org.transportsgt.privaterentalservice.services.proyeccion.SeguimientoViajeService;

/** Viaje creado por Viajes al consumir AlquilerConfirmado: enlaza el idViaje al alquiler. */
@Component
@RequiredArgsConstructor
public class ManejadorViajeProgramado implements ManejadorEvento<DatosViaje> {

    private final SeguimientoViajeService servicio;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.VIAJE_PROGRAMADO;
    }

    @Override
    public void manejar(SobreEvento<DatosViaje> evento) {
        servicio.enlazarViaje(evento.datos(), evento.versionAgregado());
    }
}
