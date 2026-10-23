package org.transportsgt.fleetservice.services.proyeccion.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.DatosSucursal;
import org.transportsgt.fleetservice.repositories.SucursalRefRepository;
import org.transportsgt.fleetservice.services.proyeccion.ProyeccionReferenciasService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProyeccionReferenciasServiceImpl implements ProyeccionReferenciasService {

    private final SucursalRefRepository sucursalRefRepository;

    @Override
    @Transactional
    public void aplicarSucursal(DatosSucursal datos, Long versionAgregado) {
        long version = versionAgregado == null ? 0L : versionAgregado;
        int aplicado = sucursalRefRepository.upsert(datos.idSucursal(), datos.nombre(), datos.activa(), version);
        log.debug("Sucursal {}: {} fila(s) aplicada(s)", datos.idSucursal(), aplicado);
    }
}
