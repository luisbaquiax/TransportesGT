package org.transportsgt.fleetservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosSucursal;
import org.transportsgt.fleetservice.repositories.SucursalRefRepository;

/**
 * Mismo mecanismo que SucursalCreada: el upsert solo aplica versiones mayores (catálogo §1.5).
 */
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
    public void manejar(SobreEvento<DatosSucursal> evento) {
        DatosSucursal datos = evento.datos();
        long version = evento.versionAgregado() == null ? 0L : evento.versionAgregado();
        int aplicado = sucursalRefRepository.upsert(datos.idSucursal(), datos.nombre(), datos.activa(), version);
        log.debug("SucursalActualizada {}: {} fila(s) aplicada(s)", datos.idSucursal(), aplicado);
    }
}
