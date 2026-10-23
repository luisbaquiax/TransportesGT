package org.transportsgt.operationalcostsservice.eventos;

import org.springframework.stereotype.Component;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.operationalcostsservice.repositories.BusRefRepository;

/** Proyecta BusRegistrado en bus_ref (idempotente por versionAgregado). */
@Component
public class ManejadorBusRegistrado extends ManejadorProyeccionBus {

    public ManejadorBusRegistrado(BusRefRepository busRefRepository) {
        super(busRefRepository);
    }

    @Override
    public TipoEvento tipo() {
        return TipoEvento.BUS_REGISTRADO;
    }
}
