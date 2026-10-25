package org.transportsgt.reportingservice.eventos.costos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosGastoTaller;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionGastosService;

/**
 * Cola {@code reportes.costos.q}, llave {@code costos.taller.registrado}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorGastoTallerRegistrado implements ManejadorEvento<DatosGastoTaller> {

    private final ProyeccionGastosService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.GASTO_TALLER_REGISTRADO;
    }

    @Override
    public void manejar(SobreEvento<DatosGastoTaller> evento) {
        proyeccion.registrarTaller(evento);
    }
}
