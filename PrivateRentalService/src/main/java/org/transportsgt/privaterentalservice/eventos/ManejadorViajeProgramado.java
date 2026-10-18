package org.transportsgt.privaterentalservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosViaje;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.repositories.AlquilerRepository;

/**
 * Viaje creado por Viajes al consumir AlquilerConfirmado: enlaza el idViaje al alquiler.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorViajeProgramado implements ManejadorEvento<DatosViaje> {

    private final AlquilerRepository alquilerRepository;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.VIAJE_PROGRAMADO;
    }

    @Override
    @Transactional
    public void manejar(SobreEvento<DatosViaje> evento) {
        DatosViaje d = evento.datos();
        if (d.idAlquiler() == null) {
            return;
        }
        alquilerRepository.findById(d.idAlquiler()).ifPresent(a -> {
            if (a.getIdViaje() == null) {
                a.setIdViaje(d.idViaje());
                alquilerRepository.save(a);
                log.debug("Alquiler {} enlazado al viaje {}", a.getId(), d.idViaje());
            }
        });
    }
}
