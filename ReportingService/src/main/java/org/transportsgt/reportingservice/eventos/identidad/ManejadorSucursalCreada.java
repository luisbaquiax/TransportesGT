package org.transportsgt.reportingservice.eventos.identidad;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosSucursal;
import org.transportsgt.reportingservice.services.proyecciones.ProyeccionSucursalService;

/**
 * Cola {@code reportes.identidad.q}, llave {@code identidad.sucursal.creada}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorSucursalCreada implements ManejadorEvento<DatosSucursal> {

    private final ProyeccionSucursalService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.SUCURSAL_CREADA;
    }

    @Override
    public void manejar(SobreEvento<DatosSucursal> evento) {
        proyeccion.aplicarSucursal(evento);
    }
}
