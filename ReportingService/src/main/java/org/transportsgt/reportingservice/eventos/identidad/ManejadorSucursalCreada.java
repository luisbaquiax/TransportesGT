package org.transportsgt.reportingservice.eventos.identidad;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.identidad.DatosSucursal;
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
