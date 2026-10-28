package org.transportsgt.reportingservice.eventos.viajes;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.viajes.DatosRuta;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionViajesService;

/**
 * Cola {@code reportes.viajes.q}, llave {@code viajes.ruta.creada}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorRutaCreada implements ManejadorEvento<DatosRuta> {

    private final ProyeccionViajesService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.RUTA_CREADA;
    }

    @Override
    public void manejar(SobreEvento<DatosRuta> evento) {
        proyeccion.aplicarRuta(evento);
    }
}
