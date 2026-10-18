package org.transportsgt.privaterentalservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosSucursal;
import org.transportsgt.privaterentalservice.repositories.SucursalRefRepository;

/** Proyección de sucursales de Identidad. Idempotente por versión. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorSucursalCreada implements ManejadorEvento<DatosSucursal> {

    private final SucursalRefRepository repository;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.SUCURSAL_CREADA;
    }

    @Override
    public void manejar(SobreEvento<DatosSucursal> evento) {
        DatosSucursal d = evento.datos();
        long version = evento.versionAgregado() == null ? 0L : evento.versionAgregado();
        repository.upsert(d.idSucursal(), d.nombre(), d.activa(), version);
        log.debug("SucursalCreada {} aplicada", d.idSucursal());
    }
}
