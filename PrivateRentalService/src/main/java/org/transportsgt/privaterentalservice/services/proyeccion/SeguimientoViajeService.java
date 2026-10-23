package org.transportsgt.privaterentalservice.services.proyeccion;

import org.transportsgt.eventos.payload.DatosLlegada;
import org.transportsgt.eventos.payload.DatosViaje;
import org.transportsgt.eventos.payload.DatosViajeEliminado;

/** Lo que pasa en Viajes con el viaje de un alquiler confirmado. Ignora los viajes que no son de alquiler. */
public interface SeguimientoViajeService {

    /** ViajeProgramado: Viajes creó el viaje al consumir AlquilerConfirmado. */
    void enlazarViaje(DatosViaje datos, Long versionAgregado);

    /** ViajeActualizado: refleja el bus y el chofer asignados (PATCH /viajes/{id}/asignacion en Viajes). */
    void actualizarRecursos(DatosViaje datos, Long versionAgregado);

    /** ViajeEliminado: cancela el alquiler (saga §4.4) y publica AlquilerCancelado para el reembolso. */
    void cancelarPorViajeEliminado(DatosViajeEliminado datos);

    /** LlegadaRegistrada: CONFIRMADO → FINALIZADO. */
    void finalizarPorLlegada(DatosLlegada datos);
}
