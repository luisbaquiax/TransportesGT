package org.transportsgt.tripservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.BusAsignado;
import org.transportsgt.eventos.payload.ChoferAsignado;
import org.transportsgt.tripservice.models.BusRef;
import org.transportsgt.tripservice.models.ChoferRef;
import org.transportsgt.tripservice.repositories.BusRefRepository;
import org.transportsgt.tripservice.repositories.ChoferRefRepository;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Bus y chofer resueltos (placa, capacidad, nombre) para los eventos ViajeProgramado y ViajeActualizado. */
@Component
@RequiredArgsConstructor
public class RecursosEvento {

    private final BusRefRepository busRefRepository;
    private final ChoferRefRepository choferRefRepository;

    public BusAsignado bus(UUID idBus) {
        return idBus == null ? null : busRefRepository.findById(idBus).map(this::bus).orElse(null);
    }

    public ChoferAsignado chofer(UUID idChofer) {
        return idChofer == null ? null : choferRefRepository.findById(idChofer).map(this::chofer).orElse(null);
    }

    /** Varios buses en una consulta (republicación). */
    public Map<UUID, BusAsignado> buses(Collection<UUID> ids) {
        return busRefRepository.findAllById(ids).stream().collect(Collectors.toMap(BusRef::getId, this::bus));
    }

    /** Varios choferes en una consulta (republicación). */
    public Map<UUID, ChoferAsignado> choferes(Collection<UUID> ids) {
        return choferRefRepository.findAllById(ids).stream().collect(Collectors.toMap(ChoferRef::getId, this::chofer));
    }

    private BusAsignado bus(BusRef b) {
        return new BusAsignado(b.getId(), b.getPlaca(), b.getCapacidadPasajeros());
    }

    private ChoferAsignado chofer(ChoferRef c) {
        return new ChoferAsignado(c.getId(), c.getNombreCompleto());
    }
}
