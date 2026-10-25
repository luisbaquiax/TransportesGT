package org.transportsgt.reportingservice.eventos.costos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosTarifaDepreciacion;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionGastosService;

/**
 * Cola {@code reportes.costos.q}, llave {@code costos.depreciacion.tarifa-cambiada}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorTarifaDepreciacionCambiada implements ManejadorEvento<DatosTarifaDepreciacion> {

    private final ProyeccionGastosService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.TARIFA_DEPRECIACION_CAMBIADA;
    }

    @Override
    public void manejar(SobreEvento<DatosTarifaDepreciacion> evento) {
        proyeccion.cambiarTarifa(evento);
    }
}
