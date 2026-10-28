package org.transportsgt.notificationservice.services.proyeccion.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.identidad.DatosUsuario;
import org.transportsgt.notificationservice.repositories.ContactoRepository;
import org.transportsgt.notificationservice.services.proyeccion.ProyeccionContactosService;

@Service
@RequiredArgsConstructor
public class ProyeccionContactosServiceImpl implements ProyeccionContactosService {

    private final ContactoRepository contactoRepository;

    @Override
    @Transactional
    public boolean aplicar(DatosUsuario datos, Long versionAgregado) {
        return contactoRepository.upsert(datos.idUsuario(), datos.correo(), datos.nombreCompleto(), datos.rol().name(),
                datos.idSucursal(), datos.activo(), versionAgregado == null ? 0L : versionAgregado) == 1;
    }
}
