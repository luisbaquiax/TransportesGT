package org.transportsgt.fleetservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosLlegada;
import org.transportsgt.fleetservice.repositories.BusRepository;

/**
 * Llegada de un viaje: el kilometraje nunca baja y el bus vuelve a DISPONIBLE solo si seguía
 * EN_TRANSITO (no pisa un EN_TALLER que haya puesto el administrador). Sin evento de salida.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorLlegadaRegistrada implements ManejadorEvento<DatosLlegada> {

    private final BusRepository busRepository;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.LLEGADA_REGISTRADA;
    }

    @Override
    public void manejar(SobreEvento<DatosLlegada> evento) {
        DatosLlegada datos = evento.datos();
        int filas = busRepository.registrarLlegada(datos.idBus(), datos.kilometrajeFinal());
        if (filas == 0) {
            log.warn("LlegadaRegistrada del viaje {}: el bus {} no existe en flota", datos.idViaje(), datos.idBus());
        } else {
            log.debug("Bus {} de vuelta con kilometraje {}", datos.idBus(), datos.kilometrajeFinal());
        }
    }
}
