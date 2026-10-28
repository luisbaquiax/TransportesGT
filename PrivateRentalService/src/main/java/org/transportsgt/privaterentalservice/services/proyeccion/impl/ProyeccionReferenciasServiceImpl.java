package org.transportsgt.privaterentalservice.services.proyeccion.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.cartera.DatosPerfilClienteActualizado;
import org.transportsgt.eventos.payload.identidad.DatosSucursal;
import org.transportsgt.privaterentalservice.repositories.ClienteRefRepository;
import org.transportsgt.privaterentalservice.repositories.SucursalRefRepository;
import org.transportsgt.privaterentalservice.services.comun.NormalizadorNit;
import org.transportsgt.privaterentalservice.services.proyeccion.ProyeccionReferenciasService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProyeccionReferenciasServiceImpl implements ProyeccionReferenciasService {

    private final SucursalRefRepository sucursalRepository;
    private final ClienteRefRepository clienteRepository;
    private final NormalizadorNit normalizadorNit;

    @Override
    @Transactional
    public void aplicarSucursal(DatosSucursal d, Long versionAgregado) {
        int aplicados = sucursalRepository.upsert(d.idSucursal(), d.nombre(), d.activa(), version(versionAgregado));
        log.debug("Sucursal {} {}", d.idSucursal(), aplicados > 0 ? "aplicada" : "ignorada (versión vieja)");
    }

    /**
     * El NIT llega nulo mientras el perfil está incompleto; se guarda normalizado para el historial por NIT.
     */
    @Override
    @Transactional
    public void aplicarCliente(DatosPerfilClienteActualizado d, Long versionAgregado) {
        int aplicados = clienteRepository.upsert(d.idUsuario(), normalizadorNit.normalizar(d.nit()),
                d.nombreCompleto(), d.activo(), version(versionAgregado));
        log.debug("Perfil de cliente {} {}", d.idUsuario(), aplicados > 0 ? "aplicado" : "ignorado (versión vieja)");
    }

    private long version(Long versionAgregado) {
        return versionAgregado == null ? 0L : versionAgregado;
    }
}
