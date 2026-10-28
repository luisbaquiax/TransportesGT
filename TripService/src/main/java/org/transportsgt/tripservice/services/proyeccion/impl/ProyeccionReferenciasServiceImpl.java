package org.transportsgt.tripservice.services.proyeccion.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.transportsgt.eventos.payload.flota.DatosBus;
import org.transportsgt.eventos.payload.flota.DatosChofer;
import org.transportsgt.eventos.payload.identidad.DatosSucursal;
import org.transportsgt.tripservice.repositories.BusRefRepository;
import org.transportsgt.tripservice.repositories.ChoferRefRepository;
import org.transportsgt.tripservice.repositories.SucursalRefRepository;
import org.transportsgt.tripservice.services.proyeccion.ProyeccionReferenciasService;

@Service
@RequiredArgsConstructor
public class ProyeccionReferenciasServiceImpl implements ProyeccionReferenciasService {

    private final SucursalRefRepository sucursalRefRepository;
    private final BusRefRepository busRefRepository;
    private final ChoferRefRepository choferRefRepository;

    @Override
    public void aplicarSucursal(DatosSucursal d, Long versionAgregado) {
        sucursalRefRepository.upsert(d.idSucursal(), d.nombre(), d.activa(), version(versionAgregado));
    }

    @Override
    public void aplicarBus(DatosBus d, Long versionAgregado) {
        busRefRepository.upsert(d.idBus(), d.idSucursal(), d.placa(), (short) d.capacidadPasajeros(),
                d.estadoOperativo().name(), d.activo(), version(versionAgregado));
    }

    @Override
    public void aplicarChofer(DatosChofer d, Long versionAgregado) {
        choferRefRepository.upsert(d.idChofer(), d.idUsuario(), d.idSucursal(), d.nombreCompleto(),
                d.salarioBaseViaje(), d.fechaVencimientoLicencia(), d.activo(), version(versionAgregado));
    }

    private long version(Long versionAgregado) {
        return versionAgregado == null ? 0L : versionAgregado;
    }
}
