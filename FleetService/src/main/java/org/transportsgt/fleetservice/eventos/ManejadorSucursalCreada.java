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
 * Proyección de sucursales de Identidad. Idempotente y a prueba de desorden:
 * el upsert solo aplica si la versión del evento es mayor que la guardada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorSucursalCreada implements ManejadorEvento<DatosSucursal> {

    private final SucursalRefRepository sucursalRefRepository;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.SUCURSAL_CREADA;
    }

    @Override
    public void manejar(SobreEvento<DatosSucursal> evento) {
        DatosSucursal datos = evento.datos();
        long version = evento.versionAgregado() == null ? 0L : evento.versionAgregado();
        int aplicado = sucursalRefRepository.upsert(datos.idSucursal(), datos.nombre(), datos.activa(), version);
        log.debug("SucursalCreada {}: {} fila(s) aplicada(s)", datos.idSucursal(), aplicado);
    }
}
