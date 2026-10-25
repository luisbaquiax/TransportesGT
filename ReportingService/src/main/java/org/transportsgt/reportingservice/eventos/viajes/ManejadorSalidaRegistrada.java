package org.transportsgt.reportingservice.eventos.viajes;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosSalida;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionViajesService;

/**
 * Cola {@code reportes.viajes.q}, llave {@code viajes.viaje.salida-registrada}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorSalidaRegistrada implements ManejadorEvento<DatosSalida> {

    private final ProyeccionViajesService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.SALIDA_REGISTRADA;
    }

    @Override
    public void manejar(SobreEvento<DatosSalida> evento) {
        proyeccion.registrarSalida(evento);
    }
}
