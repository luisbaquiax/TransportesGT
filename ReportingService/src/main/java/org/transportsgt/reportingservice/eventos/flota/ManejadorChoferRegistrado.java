package org.transportsgt.reportingservice.eventos.flota;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.flota.DatosChofer;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionFlotaService;

/**
 * Cola {@code reportes.flota.q}, llave {@code flota.chofer.registrado}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorChoferRegistrado implements ManejadorEvento<DatosChofer> {

    private final ProyeccionFlotaService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.CHOFER_REGISTRADO;
    }

    @Override
    public void manejar(SobreEvento<DatosChofer> evento) {
        proyeccion.aplicarChofer(evento);
    }
}
