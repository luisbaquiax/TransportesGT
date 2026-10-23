package org.transportsgt.privaterentalservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosViajeEliminado;
import org.transportsgt.privaterentalservice.services.proyeccion.SeguimientoViajeService;

/** ViajeEliminado: cancela el alquiler (saga §4.4) y publica AlquilerCancelado para que Cartera reembolse. */
@Component
@RequiredArgsConstructor
public class ManejadorViajeEliminado implements ManejadorEvento<DatosViajeEliminado> {

    private final SeguimientoViajeService servicio;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.VIAJE_ELIMINADO;
    }

    @Override
    public void manejar(SobreEvento<DatosViajeEliminado> evento) {
        servicio.cancelarPorViajeEliminado(evento.datos());
    }
}
