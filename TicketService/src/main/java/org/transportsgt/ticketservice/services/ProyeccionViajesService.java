package org.transportsgt.ticketservice.services;

import org.transportsgt.eventos.payload.DatosViaje;

/**
 * Mantiene {@code viaje_ref}, la copia local de los viajes REGULARES.
 */
public interface ProyeccionViajesService {

    /**
     * Aplica ViajeProgramado / ViajeActualizado (instantánea). Ignora los viajes de alquiler. Si el viaje no existe lo
     * crea (aunque llegue primero un Actualizado); si existe, solo aplica el evento si su versión es mayor.
     *
     * @return {@code true} si cambió {@code viaje_ref}
     */
    boolean aplicar(DatosViaje datos, Long versionAgregado);
}
