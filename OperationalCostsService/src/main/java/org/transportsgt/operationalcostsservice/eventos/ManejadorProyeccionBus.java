package org.transportsgt.operationalcostsservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.payload.flota.DatosBus;
import org.transportsgt.operationalcostsservice.repositories.BusRefRepository;

/**
 * Lógica común de proyección de eventos de bus en {@code bus_ref}. Cada subclase solo
 * declara el {@link TipoEvento} que atiende, evitando duplicar el cuerpo del manejador.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class ManejadorProyeccionBus implements ManejadorEvento<DatosBus> {

    private final BusRefRepository busRefRepository;

    @Override
    @Transactional
    public void manejar(SobreEvento<DatosBus> evento) {
        DatosBus d = evento.datos();
        long version = evento.versionAgregado() == null ? 0 : evento.versionAgregado();
        busRefRepository.upsert(d.idBus(), d.idSucursal(), d.placa(), d.activo(), version);
        log.debug("bus_ref {} proyectado ({})", d.idBus(), tipo());
    }
}
