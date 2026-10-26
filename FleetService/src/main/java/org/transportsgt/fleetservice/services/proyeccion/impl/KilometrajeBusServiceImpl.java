package org.transportsgt.fleetservice.services.proyeccion.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.DatosLlegada;
import org.transportsgt.eventos.payload.DatosSalida;
import org.transportsgt.fleetservice.repositories.BusRepository;
import org.transportsgt.fleetservice.services.proyeccion.KilometrajeBusService;

@Slf4j
@Service
@RequiredArgsConstructor
public class KilometrajeBusServiceImpl implements KilometrajeBusService {

    private final BusRepository busRepository;

    @Override
    @Transactional
    public void registrarSalida(DatosSalida datos) {
        int filas = busRepository.registrarSalida(datos.idBus(), datos.kilometrajeInicial());
        if (filas == 0) {
            log.warn("SalidaRegistrada del viaje {}: el bus {} no existe en flota", datos.idViaje(), datos.idBus());
        } else {
            log.debug("Bus {} a EN_TRANSITO con kilometraje {}", datos.idBus(), datos.kilometrajeInicial());
        }
    }

    @Override
    @Transactional
    public void registrarLlegada(DatosLlegada datos) {
        int filas = busRepository.registrarLlegada(datos.idBus(), datos.kilometrajeFinal());
        if (filas == 0) {
            log.warn("LlegadaRegistrada del viaje {}: el bus {} no existe en flota", datos.idViaje(), datos.idBus());
        } else {
            log.debug("Bus {} de vuelta con kilometraje {}", datos.idBus(), datos.kilometrajeFinal());
        }
    }
}
