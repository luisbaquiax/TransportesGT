package org.transportsgt.tripservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.identidad.DatosSucursal;
import org.transportsgt.tripservice.services.proyeccion.ProyeccionReferenciasService;

/** Proyección de sucursales de Identidad. Idempotente por versión. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorSucursalCreada implements ManejadorEvento<DatosSucursal> {

    private final ProyeccionReferenciasService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.SUCURSAL_CREADA;
    }

    @Override
    public void manejar(SobreEvento<DatosSucursal> evento) {
        DatosSucursal d = evento.datos();
        proyeccion.aplicarSucursal(d, evento.versionAgregado());
        log.debug("SucursalCreada {} aplicada", d.idSucursal());
    }
}
