package org.transportsgt.fleetservice.services.bus.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.EstadoOperativo;
import org.transportsgt.fleetservice.exception.EstadoOperativoInvalidoException;
import org.transportsgt.fleetservice.exception.PlacaDuplicadaException;
import org.transportsgt.fleetservice.repositories.BusRepository;

import java.util.UUID;

/**
 * Reglas de los datos del bus que se validan antes de escribir (uq_bus_placa es la última defensa).
 */
@Component
@RequiredArgsConstructor
class ValidadorBus {

    private final BusRepository busRepository;

    void placaDisponible(String placa) {
        if (busRepository.existsByPlacaIgnoreCase(placa)) {
            throw new PlacaDuplicadaException(placa);
        }
    }

    void placaDisponibleParaOtro(String placa, UUID idBus) {
        if (busRepository.existsByPlacaIgnoreCaseAndIdNot(placa, idBus)) {
            throw new PlacaDuplicadaException(placa);
        }
    }

    /**
     * EN_TRANSITO lo ponen la salida y la llegada de un viaje, nunca el administrador.
     */
    void estadoAsignable(EstadoOperativo estado) {
        if (estado == EstadoOperativo.EN_TRANSITO) {
            throw new EstadoOperativoInvalidoException(
                    "EN_TRANSITO solo cambia al registrar la salida y la llegada de un viaje");
        }
    }
}
