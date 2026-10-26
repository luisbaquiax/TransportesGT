package org.transportsgt.tripservice.mappers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.tripservice.dto.response.Responses;
import org.transportsgt.tripservice.models.BusRef;
import org.transportsgt.tripservice.models.ChoferRef;
import org.transportsgt.tripservice.models.RegistroLlegada;
import org.transportsgt.tripservice.models.RegistroSalida;
import org.transportsgt.tripservice.models.Viaje;
import org.transportsgt.tripservice.repositories.BusRefRepository;
import org.transportsgt.tripservice.repositories.ChoferRefRepository;
import org.transportsgt.tripservice.repositories.RegistroLlegadaRepository;
import org.transportsgt.tripservice.repositories.RegistroSalidaRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EnsambladorViajes {

    private final BusRefRepository busRefRepository;
    private final ChoferRefRepository choferRefRepository;
    private final RegistroSalidaRepository registroSalidaRepository;
    private final RegistroLlegadaRepository registroLlegadaRepository;
    private final ViajeMapper viajeMapper;

    public Responses.ViajeResponse respuesta(Viaje viaje) {
        return respuestas(List.of(viaje)).getFirst();
    }

    public List<Responses.ViajeResponse> respuestas(List<Viaje> viajes) {
        if (viajes.isEmpty()) {
            return List.of();
        }
        Set<UUID> ids = viajes.stream().map(Viaje::getId).collect(Collectors.toSet());
        Map<UUID, BusRef> buses = buses(viajes);
        Map<UUID, ChoferRef> choferes = porId(choferRefRepository.findAllById(
                idsNoNulos(viajes, Viaje::getIdChofer)), ChoferRef::getId);
        Map<UUID, RegistroSalida> salidas = porId(registroSalidaRepository.findAllByViajeIdIn(ids),
                salida -> salida.getViaje().getId());
        Map<UUID, RegistroLlegada> llegadas = porId(registroLlegadaRepository.findAllByViajeIdIn(ids),
                llegada -> llegada.getViaje().getId());
        return viajes.stream()
                .map(viaje -> {
                    BusRef bus = viaje.getIdBus() == null ? null : buses.get(viaje.getIdBus());
                    ChoferRef chofer = viaje.getIdChofer() == null ? null : choferes.get(viaje.getIdChofer());
                    RegistroSalida salida = salidas.get(viaje.getId());
                    RegistroLlegada llegada = llegadas.get(viaje.getId());
                    return viajeMapper.toResponse(viaje,
                            bus == null ? null : viajeMapper.toBusResumen(bus),
                            chofer == null ? null : viajeMapper.toChoferResumen(chofer),
                            salida == null ? null : viajeMapper.toResponse(salida),
                            llegada == null ? null : viajeMapper.toResponse(llegada));
                })
                .toList();
    }

    /**
     * Resumen público: solo la placa del bus, sin chofer ni registros.
     */
    public List<Responses.ViajeResumenResponse> resumenes(List<Viaje> viajes) {
        Map<UUID, BusRef> buses = buses(viajes);
        return viajes.stream()
                .map(viaje -> {
                    BusRef bus = viaje.getIdBus() == null ? null : buses.get(viaje.getIdBus());
                    return viajeMapper.toResumen(viaje, bus == null ? null : bus.getPlaca());
                })
                .toList();
    }

    private Map<UUID, BusRef> buses(List<Viaje> viajes) {
        Set<UUID> ids = idsNoNulos(viajes, Viaje::getIdBus);
        return ids.isEmpty() ? Map.of() : porId(busRefRepository.findAllById(ids), BusRef::getId);
    }

    private Set<UUID> idsNoNulos(List<Viaje> viajes, Function<Viaje, UUID> campo) {
        return viajes.stream().map(campo).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private <T> Map<UUID, T> porId(List<T> elementos, Function<T, UUID> id) {
        return elementos.stream().collect(Collectors.toMap(id, Function.identity(), (primero, otro) -> primero));
    }
}
