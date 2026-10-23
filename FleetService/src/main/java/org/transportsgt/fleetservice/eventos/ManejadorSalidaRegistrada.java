package org.transportsgt.fleetservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosSalida;
import org.transportsgt.fleetservice.services.proyeccion.KilometrajeBusService;

/** Salida de un viaje: el bus pasa a EN_TRANSITO y fija su kilometraje inicial (nunca baja). */
@Component
@RequiredArgsConstructor
public class ManejadorSalidaRegistrada implements ManejadorEvento<DatosSalida> {

    private final KilometrajeBusService servicio;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.SALIDA_REGISTRADA;
    }

    @Override
    public void manejar(SobreEvento<DatosSalida> evento) {
        servicio.registrarSalida(evento.datos());
    }
}
