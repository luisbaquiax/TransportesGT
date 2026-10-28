package org.transportsgt.tripservice.services.proyeccion;

import org.transportsgt.eventos.payload.alquileres.DatosAlquilerConfirmado;

/** Viajes de alquiler privado: no se crean por API, nacen al consumir AlquilerConfirmado. */
public interface ViajesAlquilerService {

    /**
     * Crea el viaje en PENDIENTE_ASIGNACION (idempotente por idAlquiler) y publica ViajeProgramado. Un alquiler,
     * sea de ida o de ida y vuelta, genera un solo viaje.
     */
    void crearDesdeAlquiler(DatosAlquilerConfirmado datos);
}
