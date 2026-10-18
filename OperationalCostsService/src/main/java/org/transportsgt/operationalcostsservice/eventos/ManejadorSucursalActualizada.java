package org.transportsgt.operationalcostsservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosSucursal;
import org.transportsgt.operationalcostsservice.repositories.SucursalRefRepository;

/** Proyecta SucursalActualizada en sucursal_ref (idempotente por versionAgregado). */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorSucursalActualizada implements ManejadorEvento<DatosSucursal> {

    private final SucursalRefRepository sucursalRefRepository;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.SUCURSAL_ACTUALIZADA;
    }

    @Override
    @Transactional
    public void manejar(SobreEvento<DatosSucursal> evento) {
        DatosSucursal d = evento.datos();
        long version = evento.versionAgregado() == null ? 0 : evento.versionAgregado();
        sucursalRefRepository.upsert(d.idSucursal(), d.nombre(), d.activa(), version);
        log.debug("sucursal_ref {} proyectada (SUCURSAL_ACTUALIZADA)", d.idSucursal());
    }
}
