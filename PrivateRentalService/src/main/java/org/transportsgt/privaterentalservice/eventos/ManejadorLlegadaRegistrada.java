package org.transportsgt.privaterentalservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosLlegada;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.repositories.AlquilerRepository;

/**
 * LlegadaRegistrada de un viaje de alquiler: cierra el alquiler (CONFIRMADO → FINALIZADO).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorLlegadaRegistrada implements ManejadorEvento<DatosLlegada> {

    private final AlquilerRepository alquilerRepository;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.LLEGADA_REGISTRADA;
    }

    @Override
    @Transactional
    public void manejar(SobreEvento<DatosLlegada> evento) {
        DatosLlegada d = evento.datos();
        if (d.idAlquiler() == null) {
            return;
        }
        alquilerRepository.findById(d.idAlquiler()).ifPresent(a -> {
            if (a.getEstado() != EstadoAlquiler.CONFIRMADO) {
                log.debug("Alquiler {} en estado {} no se finaliza", a.getId(), a.getEstado());
                return;
            }
            a.setEstado(EstadoAlquiler.FINALIZADO);
            alquilerRepository.save(a);
            log.info("Alquiler {} finalizado tras la llegada del viaje {}", a.getId(), d.idViaje());
        });
    }
}
