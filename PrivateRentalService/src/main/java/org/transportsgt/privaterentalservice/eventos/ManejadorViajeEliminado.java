package org.transportsgt.privaterentalservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosViajeEliminado;
import org.transportsgt.privaterentalservice.models.Alquiler;
import org.transportsgt.privaterentalservice.models.EstadoAlquiler;
import org.transportsgt.privaterentalservice.repositories.AlquilerRepository;

import java.time.OffsetDateTime;

/**
 * ViajeEliminado de un alquiler confirmado: cancela el alquiler (saga §4.4) y publica
 * AlquilerCancelado para que Cartera reembolse (solo si tiene idPago).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorViajeEliminado implements ManejadorEvento<DatosViajeEliminado> {

    static final String MOTIVO_VIAJE_ELIMINADO = "VIAJE_ELIMINADO";

    private final AlquilerRepository alquilerRepository;
    private final EventosAlquiler eventos;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.VIAJE_ELIMINADO;
    }

    @Override
    @Transactional
    public void manejar(SobreEvento<DatosViajeEliminado> evento) {
        DatosViajeEliminado d = evento.datos();
        if (d.idAlquiler() == null) {
            return;
        }
        alquilerRepository.findById(d.idAlquiler()).ifPresent(a -> {
            if (a.getEstado() != EstadoAlquiler.CONFIRMADO && a.getEstado() != EstadoAlquiler.COTIZADO) {
                log.debug("Alquiler {} en estado {} no se cancela por viaje eliminado", a.getId(), a.getEstado());
                return;
            }
            a.setEstado(EstadoAlquiler.CANCELADO);
            a.setMotivoCancelacion(MOTIVO_VIAJE_ELIMINADO);
            a.setCanceladoEn(OffsetDateTime.now());
            alquilerRepository.save(a);
            eventos.alquilerCancelado(a, MOTIVO_VIAJE_ELIMINADO);
            log.info("Alquiler {} cancelado por eliminación del viaje {}", a.getId(), d.idViaje());
        });
    }
}
