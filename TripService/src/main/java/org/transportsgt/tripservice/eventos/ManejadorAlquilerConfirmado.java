package org.transportsgt.tripservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosAlquilerConfirmado;
import org.transportsgt.eventos.payload.EstadoViaje;
import org.transportsgt.eventos.payload.TipoViaje;
import org.transportsgt.tripservice.models.Viaje;
import org.transportsgt.tripservice.repositories.ViajeRepository;

import java.util.UUID;

/**
 * AlquilerConfirmado: crea el viaje de alquiler en PENDIENTE_ASIGNACION (idempotente por idAlquiler)
 * y publica ViajeProgramado. Un alquiler, sea de ida o de ida y vuelta, genera un solo viaje.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorAlquilerConfirmado implements ManejadorEvento<DatosAlquilerConfirmado> {

    private final ViajeRepository viajeRepository;
    private final EventosViaje eventos;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.ALQUILER_CONFIRMADO;
    }

    @Override
    @Transactional
    public void manejar(SobreEvento<DatosAlquilerConfirmado> evento) {
        DatosAlquilerConfirmado d = evento.datos();
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
