package org.transportsgt.operationalcostsservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.payload.identidad.DatosSucursal;
import org.transportsgt.operationalcostsservice.repositories.SucursalRefRepository;

/**
 * Lógica común de proyección de eventos de sucursal en {@code sucursal_ref}. Cada subclase
 * solo declara el {@link TipoEvento} que atiende, evitando duplicar el cuerpo del manejador.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class ManejadorProyeccionSucursal implements ManejadorEvento<DatosSucursal> {

    private final SucursalRefRepository sucursalRefRepository;

    @Override
    @Transactional
    public void manejar(SobreEvento<DatosSucursal> evento) {
        DatosSucursal d = evento.datos();
        long version = evento.versionAgregado() == null ? 0 : evento.versionAgregado();
        sucursalRefRepository.upsert(d.idSucursal(), d.nombre(), d.activa(), version);
        log.debug("sucursal_ref {} proyectada ({})", d.idSucursal(), tipo());
    }
}
