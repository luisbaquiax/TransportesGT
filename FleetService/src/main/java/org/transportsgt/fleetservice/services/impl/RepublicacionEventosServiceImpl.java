package org.transportsgt.fleetservice.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.fleetservice.dto.response.RepublicacionResponse;
import org.transportsgt.fleetservice.eventos.EventosFlota;
import org.transportsgt.fleetservice.repositories.BusRepository;
import org.transportsgt.fleetservice.repositories.ChoferRepository;
import org.transportsgt.fleetservice.services.RepublicacionEventosService;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepublicacionEventosServiceImpl implements RepublicacionEventosService {

    private final BusRepository busRepository;
    private final ChoferRepository choferRepository;
    private final EventosFlota eventos;

    @Override
    @Transactional
    public RepublicacionResponse republicar() {
        var buses = busRepository.findAllByOrderByCreadoEn();
        buses.forEach(eventos::busRegistrado);

        var choferes = choferRepository.findAllByOrderByCreadoEn();
        choferes.forEach(eventos::choferRegistrado);

        log.info("Republicación: {} buses y {} choferes guardados en el outbox", buses.size(), choferes.size());
        return new RepublicacionResponse(buses.size(), choferes.size());
    }
}
