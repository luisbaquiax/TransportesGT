package org.transportsgt.ticketservice.services.proyeccion.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.ticketservice.repositories.ClienteRefRepository;
import org.transportsgt.ticketservice.services.proyeccion.ProyeccionClientesService;
import org.transportsgt.eventos.payload.DatosPerfilClienteActualizado;

@Service
@RequiredArgsConstructor
public class ProyeccionClientesServiceImpl implements ProyeccionClientesService {

    private final ClienteRefRepository clienteRefRepository;

    @Override
    @Transactional
    public boolean aplicar(DatosPerfilClienteActualizado datos, Long versionAgregado) {
        return clienteRefRepository.upsert(datos.idUsuario(), datos.nit(), datos.nombreCompleto(), datos.activo(),
                versionAgregado == null ? 0L : versionAgregado) == 1;
    }
}
