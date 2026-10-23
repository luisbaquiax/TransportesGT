package org.transportsgt.fleetservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosSucursal;
import org.transportsgt.fleetservice.services.proyeccion.ProyeccionReferenciasService;

/** Misma proyección que SucursalCreada: actualiza nombre y activa si la versión es mayor. */
@Component
@RequiredArgsConstructor
public class ManejadorSucursalActualizada implements ManejadorEvento<DatosSucursal> {

    private final ProyeccionReferenciasService servicio;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.SUCURSAL_ACTUALIZADA;
    }

    @Override
    public void manejar(SobreEvento<DatosSucursal> evento) {
        servicio.aplicarSucursal(evento.datos(), evento.versionAgregado());
    }
}
