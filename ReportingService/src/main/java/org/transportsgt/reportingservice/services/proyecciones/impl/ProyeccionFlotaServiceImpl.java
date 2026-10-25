package org.transportsgt.reportingservice.services.proyecciones.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.payload.DatosBus;
import org.transportsgt.eventos.payload.DatosChofer;
import org.transportsgt.reportingservice.repositories.RepBusRepository;
import org.transportsgt.reportingservice.repositories.RepChoferRepository;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionFlotaService;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProyeccionFlotaServiceImpl implements ProyeccionFlotaService {

    private final CamposObligatorios campos;
    private final RepBusRepository busRepository;
    private final RepChoferRepository choferRepository;
    private final VersionAgregado versionAgregado;

    @Override
    @Transactional
    public void aplicarBus(SobreEvento<DatosBus> evento) {
        DatosBus datos = campos.datos(evento);
        campos.exigir(evento, "idBus", datos.idBus());
        int filas = busRepository.upsert(datos.idBus(), datos.idSucursal(), datos.placa(), datos.marca(),
                datos.modelo(), (short) datos.capacidadPasajeros(),
                datos.estadoOperativo() == null ? null : datos.estadoOperativo().name(), datos.activo(),
                versionAgregado.de(evento));
        log.info("{} {} bus {} ({}) idCorrelacion={}", evento.tipoEvento(), evento.idEvento(), datos.idBus(),
                filas == 0 ? "versión vieja, ignorado" : "aplicado", evento.idCorrelacion());
    }

    @Override
    @Transactional
    public void aplicarChofer(SobreEvento<DatosChofer> evento) {
        DatosChofer datos = campos.datos(evento);
        campos.exigir(evento, "idChofer", datos.idChofer());
        int filas = choferRepository.upsert(datos.idChofer(), datos.idSucursal(), datos.numeroLicencia(),
                datos.nombreCompleto(), datos.tipoLicencia() == null ? null : datos.tipoLicencia().name(),
                datos.fechaVencimientoLicencia(), datos.activo(), versionAgregado.de(evento));
        log.info("{} {} chofer {} ({}) idCorrelacion={}", evento.tipoEvento(), evento.idEvento(), datos.idChofer(),
                filas == 0 ? "versión vieja, ignorado" : "aplicado", evento.idCorrelacion());
    }
}
