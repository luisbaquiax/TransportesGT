package org.transportsgt.reportingservice.eventos.flota;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.flota.DatosBus;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionFlotaService;

/**
 * Cola {@code reportes.flota.q}, llave {@code flota.bus.actualizado}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorBusActualizado implements ManejadorEvento<DatosBus> {

    private final ProyeccionFlotaService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.BUS_ACTUALIZADO;
    }

    @Override
    public void manejar(SobreEvento<DatosBus> evento) {
        proyeccion.aplicarBus(evento);
    }
}
