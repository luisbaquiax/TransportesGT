package org.transportsgt.fleetservice.services.proyeccion;

import org.transportsgt.eventos.payload.DatosSucursal;

/**
 * Réplica local de las sucursales de Identidad. Idempotente por versión; una versión nula cuenta como 0.
 */
public interface ProyeccionReferenciasService {

    void aplicarSucursal(DatosSucursal datos, Long versionAgregado);
}
