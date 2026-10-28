package org.transportsgt.privaterentalservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.viajes.DatosViajeEliminado;
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
