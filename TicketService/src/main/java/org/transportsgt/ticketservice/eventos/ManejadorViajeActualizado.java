package org.transportsgt.ticketservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.ticketservice.services.proyeccion.ProyeccionViajesService;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.viajes.DatosViaje;

/**
 * Cola {@code boletos.viajes.q}, llave {@code viajes.viaje.actualizado}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorViajeActualizado implements ManejadorEvento<DatosViaje> {

    private final ProyeccionViajesService proyeccionViajes;
    private final ValidacionEventos validacion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.VIAJE_ACTUALIZADO;
    }

    @Override
    public void manejar(SobreEvento<DatosViaje> evento) {
        validacion.viaje(evento);
        validacion.registrar(evento, proyeccionViajes.aplicar(evento.datos(), evento.versionAgregado()));
    }
}
