package org.transportsgt.operationalcostsservice.eventos;

import org.springframework.stereotype.Component;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.operationalcostsservice.repositories.SucursalRefRepository;

/** Proyecta SucursalCreada en sucursal_ref (idempotente por versionAgregado). */
@Component
public class ManejadorSucursalCreada extends ManejadorProyeccionSucursal {

    public ManejadorSucursalCreada(SucursalRefRepository sucursalRefRepository) {
        super(sucursalRefRepository);
    }

    @Override
    public TipoEvento tipo() {
        return TipoEvento.SUCURSAL_CREADA;
    }
}
