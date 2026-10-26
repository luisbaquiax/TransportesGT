package org.transportsgt.reportingservice.eventos.flota;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosChofer;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionFlotaService;

/**
 * Cola {@code reportes.flota.q}, llave {@code flota.chofer.actualizado}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorChoferActualizado implements ManejadorEvento<DatosChofer> {

    private final ProyeccionFlotaService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.CHOFER_ACTUALIZADO;
    }

    @Override
    public void manejar(SobreEvento<DatosChofer> evento) {
        proyeccion.aplicarChofer(evento);
    }
}
