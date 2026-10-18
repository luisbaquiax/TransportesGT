package org.transportsgt.privaterentalservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.BusAsignado;
import org.transportsgt.eventos.payload.ChoferAsignado;
import org.transportsgt.eventos.payload.DatosViaje;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.repositories.AlquilerRepository;

/**
 * ViajeActualizado: refleja la asignación de bus y chofer en el alquiler (plan §3.6:
 * la asignación se hace en Viajes con PATCH /viajes/{id}/asignacion).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorViajeActualizado implements ManejadorEvento<DatosViaje> {

    private final AlquilerRepository alquilerRepository;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.VIAJE_ACTUALIZADO;
    }

    @Override
    @Transactional
    public void manejar(SobreEvento<DatosViaje> evento) {
        DatosViaje d = evento.datos();
        if (d.idAlquiler() == null) {
            return;
        }
        alquilerRepository.findById(d.idAlquiler()).ifPresent(a -> {
            boolean cambio = false;
            if (a.getIdViaje() == null) {
                a.setIdViaje(d.idViaje());
                cambio = true;
            }
            BusAsignado bus = d.bus();
            if (bus != null) {
                a.setIdBus(bus.idBus());
                a.setPlacaBus(bus.placa());
                cambio = true;
            }
            ChoferAsignado chofer = d.chofer();
            if (chofer != null) {
                a.setIdChofer(chofer.idChofer());
                a.setNombreChofer(chofer.nombreCompleto());
                cambio = true;
            }
            if (cambio) {
                alquilerRepository.save(a);
                log.debug("Alquiler {} actualizado con bus/chofer del viaje {}", a.getId(), d.idViaje());
            }
        });
    }
}
