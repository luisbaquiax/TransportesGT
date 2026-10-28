package org.transportsgt.tripservice.services.proyeccion.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerConfirmado;
import org.transportsgt.eventos.payload.viajes.EstadoViaje;
import org.transportsgt.eventos.payload.viajes.TipoViaje;
import org.transportsgt.tripservice.eventos.EventosViaje;
import org.transportsgt.tripservice.models.Viaje;
import org.transportsgt.tripservice.repositories.ViajeRepository;
import org.transportsgt.tripservice.services.proyeccion.ViajesAlquilerService;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ViajesAlquilerServiceImpl implements ViajesAlquilerService {

    private final ViajeRepository viajeRepository;
    private final EventosViaje eventos;

    @Override
    @Transactional
    public void crearDesdeAlquiler(DatosAlquilerConfirmado d) {
        if (viajeRepository.existsByIdAlquiler(d.idAlquiler())) {
            log.debug("Viaje del alquiler {} ya existe; idempotencia", d.idAlquiler());
            return;
        }
        Viaje viaje = Viaje.builder()
                .tipoViaje(TipoViaje.ALQUILER_PRIVADO)
                .estado(EstadoViaje.PENDIENTE_ASIGNACION)
                .idAlquiler(d.idAlquiler())
                .idSucursalOrigen(d.idSucursalOrigen())
                .nombreOrigen(d.origen())
                .nombreDestino(d.destino())
                .fechaHoraSalida(d.fechaHoraSalida())
                .fechaHoraLlegadaEstimada(d.fechaHoraLlegadaEstimada())
                .numeroPasajeros((short) d.numeroPasajeros())
                .creadoPor(d.idUsuarioCliente() != null ? d.idUsuarioCliente() : UUID.randomUUID())
                .build();
        viaje = viajeRepository.save(viaje);
        eventos.viajeProgramado(viaje, null, null);
        log.info("Viaje {} creado para el alquiler {} (PENDIENTE_ASIGNACION)", viaje.getId(), d.idAlquiler());
    }
}
