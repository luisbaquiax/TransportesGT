package org.transportsgt.operationalcostsservice.eventos;

import org.springframework.stereotype.Component;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.operationalcostsservice.repositories.SucursalRefRepository;

/** Proyecta SucursalActualizada en sucursal_ref (idempotente por versionAgregado). */
@Component
public class ManejadorSucursalActualizada extends ManejadorProyeccionSucursal {

    public ManejadorSucursalActualizada(SucursalRefRepository sucursalRefRepository) {
        super(sucursalRefRepository);
    }

    @Override
    public TipoEvento tipo() {
        return TipoEvento.SUCURSAL_ACTUALIZADA;
    }
}
