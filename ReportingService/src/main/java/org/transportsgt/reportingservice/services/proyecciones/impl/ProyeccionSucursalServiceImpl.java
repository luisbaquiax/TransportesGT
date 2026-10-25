package org.transportsgt.reportingservice.services.proyecciones.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.payload.DatosSucursal;
import org.transportsgt.reportingservice.repositories.RepSucursalRepository;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionSucursalService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProyeccionSucursalServiceImpl implements ProyeccionSucursalService {

    private final CamposObligatorios campos;
    private final RepSucursalRepository sucursalRepository;
    private final VersionAgregado versionAgregado;

    @Override
    @Transactional
    public void aplicarSucursal(SobreEvento<DatosSucursal> evento) {
        DatosSucursal datos = campos.datos(evento);
        campos.exigir(evento, "idSucursal", datos.idSucursal());
        int filas = sucursalRepository.upsert(datos.idSucursal(), datos.nombre(), datos.departamento(),
                datos.latitud(), datos.longitud(), datos.activa(), versionAgregado.de(evento));
        log.info("{} {} sucursal {} ({}) idCorrelacion={}", evento.tipoEvento(), evento.idEvento(),
                datos.idSucursal(), filas == 0 ? "versión vieja, ignorado" : "aplicado", evento.idCorrelacion());
    }
}
