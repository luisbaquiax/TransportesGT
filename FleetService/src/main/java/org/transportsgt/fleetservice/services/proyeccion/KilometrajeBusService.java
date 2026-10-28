package org.transportsgt.fleetservice.services.proyeccion;

import org.transportsgt.eventos.payload.viajes.DatosLlegada;
import org.transportsgt.eventos.payload.viajes.DatosSalida;

/**
 * Efecto de los viajes sobre el bus. Cambios automáticos: NO incrementan la versión ni publican BusActualizado.
 * El kilometraje nunca baja.
 */
public interface KilometrajeBusService {

    /**
     * SalidaRegistrada: el bus pasa a EN_TRANSITO con el kilometraje inicial.
     */
    void registrarSalida(DatosSalida datos);

    /**
     * LlegadaRegistrada: kilometraje final y DISPONIBLE solo si seguía EN_TRANSITO (no pisa un EN_TALLER).
     */
    void registrarLlegada(DatosLlegada datos);
}
