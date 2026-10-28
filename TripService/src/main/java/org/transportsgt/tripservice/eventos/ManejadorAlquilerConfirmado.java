package org.transportsgt.tripservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.alquileres.DatosAlquilerConfirmado;
import org.transportsgt.tripservice.services.proyeccion.ViajesAlquilerService;

/**
 * AlquilerConfirmado: crea el viaje de alquiler en PENDIENTE_ASIGNACION (idempotente por idAlquiler)
 * y publica ViajeProgramado. La regla vive en {@link ViajesAlquilerService}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorAlquilerConfirmado implements ManejadorEvento<DatosAlquilerConfirmado> {

    private final ViajesAlquilerService viajesAlquiler;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.ALQUILER_CONFIRMADO;
    }

    @Override
    public void manejar(SobreEvento<DatosAlquilerConfirmado> evento) {
        viajesAlquiler.crearDesdeAlquiler(evento.datos());
    }
}
