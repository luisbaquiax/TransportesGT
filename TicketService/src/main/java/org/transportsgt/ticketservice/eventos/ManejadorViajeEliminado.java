package org.transportsgt.ticketservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.ticketservice.services.compra.AnulacionViajeService;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.viajes.DatosViajeEliminado;

/**
 * Cola {@code boletos.viajes.q}, llave {@code viajes.viaje.eliminado}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorViajeEliminado implements ManejadorEvento<DatosViajeEliminado> {

    private final AnulacionViajeService anulacionViaje;
    private final ValidacionEventos validacion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.VIAJE_ELIMINADO;
    }

    @Override
    public void manejar(SobreEvento<DatosViajeEliminado> evento) {
        validacion.viajeEliminado(evento);
        validacion.registrar(evento, anulacionViaje.anularViaje(evento.datos().idViaje()) > 0);
    }
}
