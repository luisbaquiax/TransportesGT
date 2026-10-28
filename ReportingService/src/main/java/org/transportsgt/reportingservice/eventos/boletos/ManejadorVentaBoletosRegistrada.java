package org.transportsgt.reportingservice.eventos.boletos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.boletos.DatosVentaBoletos;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionIngresosService;

/**
 * Cola {@code reportes.boletos.q}, llave {@code boletos.venta.registrada}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorVentaBoletosRegistrada implements ManejadorEvento<DatosVentaBoletos> {

    private final ProyeccionIngresosService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.VENTA_BOLETOS_REGISTRADA;
    }

    @Override
    public void manejar(SobreEvento<DatosVentaBoletos> evento) {
        proyeccion.registrarVenta(evento);
    }
}
