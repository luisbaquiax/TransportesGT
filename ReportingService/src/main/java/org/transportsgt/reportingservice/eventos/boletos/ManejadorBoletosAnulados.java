package org.transportsgt.reportingservice.eventos.boletos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosBoletosAnulados;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionIngresosService;

/**
 * Cola {@code reportes.boletos.q}, llave {@code boletos.venta.anulada}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorBoletosAnulados implements ManejadorEvento<DatosBoletosAnulados> {

    private final ProyeccionIngresosService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.BOLETOS_ANULADOS;
    }

    @Override
    public void manejar(SobreEvento<DatosBoletosAnulados> evento) {
        proyeccion.anularVenta(evento);
    }
}
