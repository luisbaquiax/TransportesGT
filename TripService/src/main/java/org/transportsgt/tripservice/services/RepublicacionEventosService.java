package org.transportsgt.tripservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.BusAsignado;
import org.transportsgt.eventos.payload.ChoferAsignado;
import org.transportsgt.tripservice.dto.response.Responses;
import org.transportsgt.tripservice.eventos.EventosViaje;
import org.transportsgt.tripservice.models.BusRef;
import org.transportsgt.tripservice.models.ChoferRef;
import org.transportsgt.tripservice.models.Ruta;
import org.transportsgt.tripservice.models.Viaje;
import org.transportsgt.tripservice.repositories.BusRefRepository;
import org.transportsgt.tripservice.repositories.ChoferRefRepository;
import org.transportsgt.tripservice.repositories.RutaRepository;
import org.transportsgt.tripservice.repositories.ViajeRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Republica las instantáneas de rutas y viajes (RutaCreada, ViajeProgramado) para poblar las proyecciones de
 * Boletos, Alquileres y Reportes; los datos iniciales de la migración no generan eventos (plan §2.9).
 *
 * <p>Solo instantáneas: los hechos (SalidaRegistrada, LlegadaRegistrada) no se republican, porque sus
 * consumidores (Costos, Flota) los tratan como hechos nuevos. Es idempotente: cada evento lleva la versión
 * actual del agregado y los consumidores ignoran las que ya aplicaron.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RepublicacionEventosService {

    private final RutaRepository rutaRepository;
    private final ViajeRepository viajeRepository;
    private final BusRefRepository busRefRepository;
    private final ChoferRefRepository choferRefRepository;
    private final EventosViaje eventos;

    @Transactional
    public Responses.RepublicacionResponse republicar() {
        // Primero las rutas: los viajes regulares las referencian
        List<Ruta> rutas = rutaRepository.findAll(Sort.by("creadoEn"));
        rutas.forEach(eventos::rutaCreada);

        List<Viaje> viajes = viajeRepository.findAll(Sort.by("fechaHoraSalida"));
        Map<UUID, BusRef> buses = busRefRepository.findAllById(
                        viajes.stream().map(Viaje::getIdBus).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(BusRef::getId, Function.identity()));
        Map<UUID, ChoferRef> choferes = choferRefRepository.findAllById(
                        viajes.stream().map(Viaje::getIdChofer).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ChoferRef::getId, Function.identity()));
        for (Viaje viaje : viajes) {
            BusRef bus = viaje.getIdBus() == null ? null : buses.get(viaje.getIdBus());
            ChoferRef chofer = viaje.getIdChofer() == null ? null : choferes.get(viaje.getIdChofer());
            eventos.viajeProgramado(viaje,
                    bus == null ? null : new BusAsignado(bus.getId(), bus.getPlaca(), bus.getCapacidadPasajeros()),
                    chofer == null ? null : new ChoferAsignado(chofer.getId(), chofer.getNombreCompleto()));
        }
        log.info("Republicación: {} rutas y {} viajes guardados en el outbox", rutas.size(), viajes.size());
        return new Responses.RepublicacionResponse(rutas.size(), viajes.size());
    }
}
