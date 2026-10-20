package org.transportsgt.ticketservice.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.ticketservice.dto.response.AsientoMaximoResponse;
import org.transportsgt.ticketservice.dto.response.MapaAsientosResponse;
import org.transportsgt.ticketservice.dto.response.ViajeDisponibleResponse;
import org.transportsgt.ticketservice.exception.ViajeNoEncontradoException;
import org.transportsgt.ticketservice.mappers.ViajeMapper;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.BoletoRepository;
import org.transportsgt.ticketservice.repositories.ConteoAsientos;
import org.transportsgt.ticketservice.repositories.ViajeRefRepository;
import org.transportsgt.ticketservice.repositories.ViajeRefSpecs;
import org.transportsgt.ticketservice.services.ConsultaViajesService;
import org.transportsgt.ticketservice.utils.Fechas;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultaViajesServiceImpl implements ConsultaViajesService {

    private static final Sort POR_SALIDA = Sort.by("fechaHoraSalida");

    private final ViajeRefRepository viajeRefRepository;
    private final BoletoRepository boletoRepository;
    private final ViajeRefSpecs specs;
    private final ViajeMapper viajeMapper;
    private final Fechas fechas;
    private final Clock clock;

    @Override
    public List<ViajeDisponibleResponse> aLaVenta(UUID idOrigen, UUID idRuta, String destino, LocalDate fecha) {
        OffsetDateTime ahora = fechas.ahora(clock.instant());
        OffsetDateTime desde = fecha == null ? null : fechas.inicioDelDia(fecha);
        OffsetDateTime hasta = fecha == null ? null : fechas.inicioDelDia(fecha.plusDays(1));
        List<ViajeRef> viajes = viajeRefRepository.findAll(
                        specs.aLaVenta(ahora, idOrigen, idRuta, destino, desde, hasta), POR_SALIDA).stream()
                .filter(v -> v.getCapacidadBus() != null)   // sin bus no hay asientos que vender
                .toList();
        if (viajes.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> ocupados = boletoRepository.contarOcupados(viajes.stream().map(ViajeRef::getId).toList())
                .stream().collect(Collectors.toMap(ConteoAsientos::getIdViaje, ConteoAsientos::getOcupados));
        return viajes.stream()
                .map(v -> viajeMapper.toDisponible(v,
                        (int) Math.max(0, v.getCapacidadBus() - ocupados.getOrDefault(v.getId(), 0L))))
                .toList();
    }

    @Override
    public MapaAsientosResponse mapaAsientos(UUID idViaje) {
        ViajeRef viaje = viajeRefRepository.findById(idViaje)
                .filter(v -> !v.getEliminado())
                .orElseThrow(ViajeNoEncontradoException::new);
        List<Integer> ocupados = boletoRepository.findAsientosOcupados(idViaje).stream().map(Short::intValue).toList();
        return new MapaAsientosResponse(idViaje, viaje.getCapacidadBus() == null ? 0 : viaje.getCapacidadBus(), ocupados);
    }

    @Override
    public AsientoMaximoResponse asientoMaximo(UUID idViaje) {
        return new AsientoMaximoResponse(idViaje, boletoRepository.findAsientoMaximo(idViaje));
    }
}
