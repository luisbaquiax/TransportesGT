package org.transportsgt.fleetservice.services.comun;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.fleetservice.exception.BusNoEncontradoException;
import org.transportsgt.fleetservice.exception.ChoferNoEncontradoException;
import org.transportsgt.fleetservice.models.Bus;
import org.transportsgt.fleetservice.models.Chofer;
import org.transportsgt.fleetservice.repositories.BusRepository;
import org.transportsgt.fleetservice.repositories.ChoferRepository;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BuscadorFlota {

    private final BusRepository busRepository;
    private final ChoferRepository choferRepository;

    public Bus bus(UUID id) {
        return busRepository.findById(id).orElseThrow(() -> new BusNoEncontradoException(id));
    }

    public Chofer chofer(UUID id) {
        return choferRepository.findById(id).orElseThrow(() -> new ChoferNoEncontradoException(id));
    }
}
