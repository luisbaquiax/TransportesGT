package org.transportsgt.operationalcostsservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosBus;
import org.transportsgt.operationalcostsservice.repositories.BusRefRepository;

/** Proyecta BusActualizado en bus_ref (idempotente por versionAgregado). */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorBusActualizado implements ManejadorEvento<DatosBus> {

    private final BusRefRepository busRefRepository;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.BUS_ACTUALIZADO;
    }

    @Override
    @Transactional
    public void manejar(SobreEvento<DatosBus> evento) {
        DatosBus d = evento.datos();
        long version = evento.versionAgregado() == null ? 0 : evento.versionAgregado();
        busRefRepository.upsert(d.idBus(), d.idSucursal(), d.placa(), d.activo(), version);
        log.debug("bus_ref {} proyectado (BUS_ACTUALIZADO)", d.idBus());
    }
}
