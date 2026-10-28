package org.transportsgt.privaterentalservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.identidad.DatosSucursal;
import org.transportsgt.privaterentalservice.services.proyeccion.ProyeccionReferenciasService;

/** Misma proyección que SucursalCreada: actualiza nombre/activa si la versión es mayor. */
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
