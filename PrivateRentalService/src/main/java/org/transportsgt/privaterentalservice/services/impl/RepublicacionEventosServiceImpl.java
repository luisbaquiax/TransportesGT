package org.transportsgt.privaterentalservice.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.privaterentalservice.dto.response.RepublicacionResponse;
import org.transportsgt.privaterentalservice.eventos.EventosAlquiler;
import org.transportsgt.privaterentalservice.repositories.AlquilerRepository;
import org.transportsgt.privaterentalservice.services.RepublicacionEventosService;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepublicacionEventosServiceImpl implements RepublicacionEventosService {

    private final AlquilerRepository alquilerRepository;
    private final EventosAlquiler eventos;

    @Override
    @Transactional
    public RepublicacionResponse republicar() {
        var alquileres = alquilerRepository.findAll();
        alquileres.forEach(eventos::alquilerSolicitado);
        log.info("Republicación: {} alquileres guardados en el outbox", alquileres.size());
        return new RepublicacionResponse(alquileres.size());
    }
}
