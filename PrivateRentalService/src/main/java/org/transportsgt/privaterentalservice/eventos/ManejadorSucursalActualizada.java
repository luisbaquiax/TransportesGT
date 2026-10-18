package org.transportsgt.privaterentalservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosSucursal;
import org.transportsgt.privaterentalservice.repositories.SucursalRefRepository;

/** Misma proyección que SucursalCreada: actualiza nombre/activa si la versión es mayor. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorSucursalActualizada implements ManejadorEvento<DatosSucursal> {

    private final SucursalRefRepository repository;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.SUCURSAL_ACTUALIZADA;
    }

    @Override
    public void manejar(SobreEvento<DatosSucursal> evento) {
        DatosSucursal d = evento.datos();
        long version = evento.versionAgregado() == null ? 0L : evento.versionAgregado();
        repository.upsert(d.idSucursal(), d.nombre(), d.activa(), version);
        log.debug("SucursalActualizada {} aplicada", d.idSucursal());
    }
}
