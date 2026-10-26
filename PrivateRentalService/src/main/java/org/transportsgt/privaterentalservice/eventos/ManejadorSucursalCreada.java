package org.transportsgt.privaterentalservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosSucursal;
import org.transportsgt.privaterentalservice.services.proyeccion.ProyeccionReferenciasService;

/** Proyección de sucursales de Identidad. Idempotente por versión. */
@Component
@RequiredArgsConstructor
public class ManejadorSucursalCreada implements ManejadorEvento<DatosSucursal> {

    private final ProyeccionReferenciasService servicio;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.SUCURSAL_CREADA;
    }

    @Override
    public void manejar(SobreEvento<DatosSucursal> evento) {
        servicio.aplicarSucursal(evento.datos(), evento.versionAgregado());
    }
}
