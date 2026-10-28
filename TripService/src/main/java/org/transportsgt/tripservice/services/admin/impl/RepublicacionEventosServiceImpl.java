package org.transportsgt.tripservice.services.admin.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.flota.BusAsignado;
import org.transportsgt.eventos.payload.flota.ChoferAsignado;
import org.transportsgt.tripservice.dto.response.Responses;
import org.transportsgt.tripservice.eventos.EventosViaje;
import org.transportsgt.tripservice.eventos.RecursosEvento;
import org.transportsgt.tripservice.models.Ruta;
import org.transportsgt.tripservice.models.Viaje;
import org.transportsgt.tripservice.repositories.RutaRepository;
import org.transportsgt.tripservice.repositories.ViajeRepository;
import org.transportsgt.tripservice.services.admin.RepublicacionEventosService;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepublicacionEventosServiceImpl implements RepublicacionEventosService {

    private final RutaRepository rutaRepository;
    private final ViajeRepository viajeRepository;
    private final RecursosEvento recursos;
    private final EventosViaje eventos;

    @Override
    @Transactional
    public Responses.RepublicacionResponse republicar() {
        // Primero las rutas: los viajes regulares las referencian
        List<Ruta> rutas = rutaRepository.findAll(Sort.by("creadoEn"));
        rutas.forEach(eventos::rutaCreada);

        List<Viaje> viajes = viajeRepository.findAll(Sort.by("fechaHoraSalida"));
        Map<UUID, BusAsignado> buses = recursos.buses(
                viajes.stream().map(Viaje::getIdBus).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<UUID, ChoferAsignado> choferes = recursos.choferes(
                viajes.stream().map(Viaje::getIdChofer).filter(Objects::nonNull).collect(Collectors.toSet()));
        for (Viaje viaje : viajes) {
            eventos.viajeProgramado(viaje,
                    viaje.getIdBus() == null ? null : buses.get(viaje.getIdBus()),
                    viaje.getIdChofer() == null ? null : choferes.get(viaje.getIdChofer()));
        }
        log.info("Republicación: {} rutas y {} viajes guardados en el outbox", rutas.size(), viajes.size());
        return new Responses.RepublicacionResponse(rutas.size(), viajes.size());
    }
}
