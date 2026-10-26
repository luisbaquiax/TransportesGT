package org.transportsgt.operationalcostsservice.eventos;

import org.springframework.stereotype.Component;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.operationalcostsservice.repositories.BusRefRepository;

/** Proyecta BusActualizado en bus_ref (idempotente por versionAgregado). */
@Component
public class ManejadorBusActualizado extends ManejadorProyeccionBus {

    public ManejadorBusActualizado(BusRefRepository busRefRepository) {
        super(busRefRepository);
    }

    @Override
    public TipoEvento tipo() {
        return TipoEvento.BUS_ACTUALIZADO;
    }
}
