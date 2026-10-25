package org.transportsgt.reportingservice.services.proyecciones;

import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.payload.DatosSucursal;

/**
 * Proyección {@code rep_sucursal} (eventos de Identidad).
 */
public interface ProyeccionSucursalService {

    /** SucursalCreada / SucursalActualizada: upsert por versión. */
    void aplicarSucursal(SobreEvento<DatosSucursal> evento);
}
