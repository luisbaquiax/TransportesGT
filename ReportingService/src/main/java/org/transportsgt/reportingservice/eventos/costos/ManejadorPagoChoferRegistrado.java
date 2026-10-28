package org.transportsgt.reportingservice.eventos.costos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.costos.DatosPagoChofer;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionGastosService;

/**
 * Cola {@code reportes.costos.q}, llave {@code costos.pago-chofer.registrado}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorPagoChoferRegistrado implements ManejadorEvento<DatosPagoChofer> {

    private final ProyeccionGastosService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.PAGO_CHOFER_REGISTRADO;
    }

    @Override
    public void manejar(SobreEvento<DatosPagoChofer> evento) {
        proyeccion.registrarPagoChofer(evento);
    }
}
