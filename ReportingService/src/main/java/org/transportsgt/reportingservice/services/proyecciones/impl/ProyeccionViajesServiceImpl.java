package org.transportsgt.reportingservice.services.proyecciones.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.payload.viajes.DatosLlegada;
import org.transportsgt.eventos.payload.viajes.DatosRuta;
import org.transportsgt.eventos.payload.viajes.DatosRutaEliminada;
import org.transportsgt.eventos.payload.viajes.DatosSalida;
import org.transportsgt.eventos.payload.viajes.DatosViaje;
import org.transportsgt.eventos.payload.viajes.DatosViajeEliminado;
import org.transportsgt.reportingservice.exception.ProyeccionNoDisponibleException;
import org.transportsgt.reportingservice.repositories.RepBusRepository;
import org.transportsgt.reportingservice.repositories.RepRutaRepository;
import org.transportsgt.reportingservice.repositories.RepViajeRepository;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionViajesService;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProyeccionViajesServiceImpl implements ProyeccionViajesService {

    private final CamposObligatorios campos;
    private final RepRutaRepository rutaRepository;
    private final RepViajeRepository viajeRepository;
    private final RepBusRepository busRepository;
    private final VersionAgregado versionAgregado;

    @Override
    @Transactional
    public void aplicarRuta(SobreEvento<DatosRuta> evento) {
        DatosRuta datos = campos.datos(evento);
        campos.exigir(evento, "idRuta", datos.idRuta());
        int filas = rutaRepository.upsert(
                datos.idRuta(),
                datos.idSucursalOrigen(),
                datos.idSucursalDestino(),
                datos.distanciaKm(),
                datos.precioBoleto(),
                versionAgregado.de(evento));
        log.info("{} {} ruta {} ({}) idCorrelacion={}", evento.tipoEvento(), evento.idEvento(), datos.idRuta(),
                filas == 0 ? "versión vieja, ignorado" : "aplicado", evento.idCorrelacion());
    }

    @Override
    @Transactional
    public void eliminarRuta(SobreEvento<DatosRutaEliminada> evento) {
        UUID idRuta = campos.datos(evento).idRuta();
        campos.exigir(evento, "idRuta", idRuta);
        if (rutaRepository.marcarEliminada(idRuta) == 0) {
            throw new ProyeccionNoDisponibleException("La ruta " + idRuta + " aún no está proyectada");
        }
        log.info("{} {} ruta {} eliminada idCorrelacion={}", evento.tipoEvento(), evento.idEvento(), idRuta,
                evento.idCorrelacion());
    }

    @Override
    @Transactional
    public void aplicarViaje(SobreEvento<DatosViaje> evento) {
        DatosViaje datos = campos.datos(evento);
        campos.exigir(evento, "idViaje", datos.idViaje());
        campos.exigir(evento, "tipoViaje", datos.tipoViaje());
        campos.exigir(evento, "estado", datos.estado());
        int filas = viajeRepository.upsert(datos.idViaje(), datos.tipoViaje().name(), datos.estado().name(),
                datos.idRuta(), datos.idAlquiler(), datos.idSucursalOrigen(), datos.nombreOrigen(),
                datos.nombreDestino(), datos.bus() == null ? null : datos.bus().idBus(),
                datos.chofer() == null ? null : datos.chofer().idChofer(), datos.fechaHoraSalida(),
                datos.fechaHoraLlegadaEstimada(), versionAgregado.de(evento));
        log.info("{} {} viaje {} ({}) idCorrelacion={}", evento.tipoEvento(), evento.idEvento(), datos.idViaje(),
                filas == 0 ? "versión vieja, ignorado" : "aplicado", evento.idCorrelacion());
    }

    @Override
    @Transactional
    public void eliminarViaje(SobreEvento<DatosViajeEliminado> evento) {
        UUID idViaje = campos.datos(evento).idViaje();
        campos.exigir(evento, "idViaje", idViaje);
        if (viajeRepository.marcarEliminado(idViaje) == 0) {
            throw new ProyeccionNoDisponibleException("El viaje " + idViaje + " aún no está proyectado");
        }
        log.info("{} {} viaje {} eliminado idCorrelacion={}", evento.tipoEvento(), evento.idEvento(), idViaje,
                evento.idCorrelacion());
    }

    @Override
    @Transactional
    public void registrarSalida(SobreEvento<DatosSalida> evento) {
        DatosSalida datos = campos.datos(evento);
        campos.exigir(evento, "idViaje", datos.idViaje());
        exigirViaje(datos.idViaje());
        int filas = viajeRepository.registrarSalida(datos.idViaje(), datos.fechaHoraSalidaReal(),
                datos.kilometrajeInicial());
        // filas = 0: la llegada ya se aplicó (desorden); el bus no debe volver a EN_TRANSITO
        if (filas > 0) {
            busRepository.registrarSalida(datos.idBus());
        }
        log.info("{} {} viaje {} ({}) idCorrelacion={}", evento.tipoEvento(), evento.idEvento(), datos.idViaje(),
                filas == 0 ? "llegada ya registrada, ignorado" : "aplicado", evento.idCorrelacion());
    }

    @Override
    @Transactional
    public void registrarLlegada(SobreEvento<DatosLlegada> evento) {
        DatosLlegada datos = campos.datos(evento);
        campos.exigir(evento, "idViaje", datos.idViaje());
        campos.exigir(evento, "kilometrajeFinal", datos.kilometrajeFinal());
        exigirViaje(datos.idViaje());
        viajeRepository.registrarLlegada(datos.idViaje(), datos.fechaHoraSalidaReal(), datos.fechaHoraLlegadaReal(),
                datos.kilometrajeInicial(), datos.kilometrajeFinal(), datos.distanciaRecorridaKm());
        if (busRepository.registrarLlegada(datos.idBus(), datos.kilometrajeFinal()) == 0) {
            log.warn("{} {}: el bus {} aún no está proyectado; su kilometraje no se actualizó idCorrelacion={}",
                    evento.tipoEvento(), evento.idEvento(), datos.idBus(), evento.idCorrelacion());
        }
        log.info("{} {} viaje {} finalizado idCorrelacion={}", evento.tipoEvento(), evento.idEvento(),
                datos.idViaje(), evento.idCorrelacion());
    }

    private void exigirViaje(UUID idViaje) {
        if (!viajeRepository.existsById(idViaje)) {
            throw new ProyeccionNoDisponibleException("El viaje " + idViaje + " aún no está proyectado");
        }
    }
}
