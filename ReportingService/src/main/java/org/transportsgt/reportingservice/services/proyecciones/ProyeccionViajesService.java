package org.transportsgt.reportingservice.services.proyecciones;

import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.payload.DatosLlegada;
import org.transportsgt.eventos.payload.DatosRuta;
import org.transportsgt.eventos.payload.DatosRutaEliminada;
import org.transportsgt.eventos.payload.DatosSalida;
import org.transportsgt.eventos.payload.DatosViaje;
import org.transportsgt.eventos.payload.DatosViajeEliminado;

/**
 * Proyecciones {@code rep_ruta} y {@code rep_viaje} (eventos de Viajes), más el estado y kilometraje de
 * {@code rep_bus} que se derivan de la salida y la llegada.
 */
public interface ProyeccionViajesService {

    /** RutaCreada / RutaActualizada: upsert por versión. */
    void aplicarRuta(SobreEvento<DatosRuta> evento);

    /** RutaEliminada: {@code eliminada = true}; si la ruta aún no existe, error transitorio. */
    void eliminarRuta(SobreEvento<DatosRutaEliminada> evento);

    /** ViajeProgramado / ViajeActualizado: upsert por versión. */
    void aplicarViaje(SobreEvento<DatosViaje> evento);

    /** ViajeEliminado: {@code eliminado = true}; si el viaje aún no existe, error transitorio. */
    void eliminarViaje(SobreEvento<DatosViajeEliminado> evento);

    /** SalidaRegistrada: salida real en el viaje y bus EN_TRANSITO; si el viaje aún no existe, error transitorio. */
    void registrarSalida(SobreEvento<DatosSalida> evento);

    /** LlegadaRegistrada: viaje FINALIZADO, kilometraje del bus sin bajar y bus DISPONIBLE. */
    void registrarLlegada(SobreEvento<DatosLlegada> evento);
}
