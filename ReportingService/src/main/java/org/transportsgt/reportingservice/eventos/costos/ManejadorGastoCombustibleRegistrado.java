package org.transportsgt.reportingservice.eventos.costos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.costos.DatosGastoCombustible;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionGastosService;

/**
 * Cola {@code reportes.costos.q}, llave {@code costos.combustible.registrado}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorGastoCombustibleRegistrado implements ManejadorEvento<DatosGastoCombustible> {

    private final ProyeccionGastosService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.GASTO_COMBUSTIBLE_REGISTRADO;
    }

    @Override
    public void manejar(SobreEvento<DatosGastoCombustible> evento) {
        proyeccion.registrarCombustible(evento);
    }
}
