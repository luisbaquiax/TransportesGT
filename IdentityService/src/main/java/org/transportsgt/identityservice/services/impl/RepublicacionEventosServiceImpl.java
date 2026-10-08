package org.transportsgt.identityservice.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.identityservice.dto.response.RepublicacionResponse;
import org.transportsgt.identityservice.eventos.EventosIdentidad;
import org.transportsgt.identityservice.models.Sucursal;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.SucursalRepository;
import org.transportsgt.identityservice.repositories.UsuarioRepository;
import org.transportsgt.identityservice.services.RepublicacionEventosService;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepublicacionEventosServiceImpl implements RepublicacionEventosService {

    private final SucursalRepository sucursalRepository;
    private final UsuarioRepository usuarioRepository;
    private final EventosIdentidad eventos;

    @Override
    @Transactional
    public RepublicacionResponse republicar() {
        // Primero las sucursales: los consumidores de usuarios pueden necesitar la sucursal
        List<Sucursal> sucursales = sucursalRepository.findAll(Sort.by("creadoEn"));
        sucursales.forEach(eventos::sucursalCreada);

        List<Usuario> usuarios = usuarioRepository.findAllByOrderByCreadoEn();
        usuarios.forEach(eventos::usuarioCreado);

        log.info("Republicación: {} sucursales y {} usuarios guardados en el outbox", sucursales.size(), usuarios.size());
        return new RepublicacionResponse(sucursales.size(), usuarios.size());
    }
}
