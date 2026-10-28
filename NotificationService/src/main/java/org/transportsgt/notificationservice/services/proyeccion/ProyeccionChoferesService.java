package org.transportsgt.notificationservice.services.proyeccion;

import org.transportsgt.eventos.payload.flota.DatosChofer;

/**
 * Copia local de los choferes (tabla {@code chofer_licencia}) y programación de sus avisos de licencia.
 */
public interface ProyeccionChoferesService {

    /**
     * Upsert por versión. Si se aplica: chofer inactivo → cancela todos sus avisos pendientes; activo → cancela los
     * pendientes de otra fecha de vencimiento y programa los de 30, 15 y 3 días que aún no pasaron.
     *
     * @return {@code true} si se aplicó
     */
    boolean aplicar(DatosChofer datos, Long versionAgregado);
}
