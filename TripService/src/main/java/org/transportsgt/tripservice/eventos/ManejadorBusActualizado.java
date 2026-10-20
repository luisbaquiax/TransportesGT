package org.transportsgt.tripservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosBus;
import org.transportsgt.tripservice.repositories.BusRefRepository;

/** Proyección de buses de Flota. Idempotente por versión. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorBusActualizado implements ManejadorEvento<DatosBus> {

    private final BusRefRepository repository;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.BUS_ACTUALIZADO;
    }

    @Override
    public void manejar(SobreEvento<DatosBus> evento) {
        DatosBus d = evento.datos();
        long version = evento.versionAgregado() == null ? 0L : evento.versionAgregado();
        repository.upsert(d.idBus(), d.idSucursal(), d.placa(), (short) d.capacidadPasajeros(),
                d.estadoOperativo().name(), d.activo(), version);
        log.debug("BusActualizado {} aplicado", d.idBus());
    }
}
