package org.transportsgt.customerwalletservice.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.customerwalletservice.dto.response.RepublicacionResponse;
import org.transportsgt.customerwalletservice.eventos.EventosCartera;
import org.transportsgt.customerwalletservice.repositories.ClienteRepository;
import org.transportsgt.customerwalletservice.services.RepublicacionEventosService;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepublicacionEventosServiceImpl implements RepublicacionEventosService {

    private final ClienteRepository clienteRepository;
    private final EventosCartera eventos;

    @Override
    @Transactional
    public RepublicacionResponse republicar() {
        var clientes = clienteRepository.findAllByOrderByCreadoEn();
        clientes.forEach(eventos::perfilClienteActualizado);
        log.info("Republicación: {} perfiles de cliente guardados en el outbox", clientes.size());
        return new RepublicacionResponse(clientes.size());
    }
}
