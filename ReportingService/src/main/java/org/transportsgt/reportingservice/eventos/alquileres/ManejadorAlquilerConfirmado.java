package org.transportsgt.reportingservice.eventos.alquileres;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosAlquilerConfirmado;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionIngresosService;

/**
 * Cola {@code reportes.alquileres.q}, llave {@code alquileres.alquiler.confirmado}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorAlquilerConfirmado implements ManejadorEvento<DatosAlquilerConfirmado> {

    private final ProyeccionIngresosService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.ALQUILER_CONFIRMADO;
    }

    @Override
    public void manejar(SobreEvento<DatosAlquilerConfirmado> evento) {
        proyeccion.registrarAlquiler(evento);
    }
}
