package org.transportsgt.reportingservice.eventos.costos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosDepreciacion;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionGastosService;

/**
 * Cola {@code reportes.costos.q}, llave {@code costos.depreciacion.calculada}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorDepreciacionCalculada implements ManejadorEvento<DatosDepreciacion> {

    private final ProyeccionGastosService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.DEPRECIACION_CALCULADA;
    }

    @Override
    public void manejar(SobreEvento<DatosDepreciacion> evento) {
        proyeccion.registrarDepreciacion(evento);
    }
}
