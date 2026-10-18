package org.transportsgt.fleetservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosSalida;
import org.transportsgt.fleetservice.repositories.BusRepository;

/**
 * Salida de un viaje: el bus pasa a EN_TRANSITO y fija su kilometraje inicial (nunca baja).
 * Cambio automático: NO incrementa la versión ni publica BusActualizado (catálogo §2.2).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorSalidaRegistrada implements ManejadorEvento<DatosSalida> {

    private final BusRepository busRepository;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.SALIDA_REGISTRADA;
    }

    @Override
    public void manejar(SobreEvento<DatosSalida> evento) {
        DatosSalida datos = evento.datos();
        int filas = busRepository.registrarSalida(datos.idBus(), datos.kilometrajeInicial());
        if (filas == 0) {
            log.warn("SalidaRegistrada del viaje {}: el bus {} no existe en flota", datos.idViaje(), datos.idBus());
        } else {
            log.debug("Bus {} a EN_TRANSITO con kilometraje {}", datos.idBus(), datos.kilometrajeInicial());
        }
    }
}
