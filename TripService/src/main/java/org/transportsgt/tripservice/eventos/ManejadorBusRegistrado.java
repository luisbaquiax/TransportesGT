package org.transportsgt.tripservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosBus;
import org.transportsgt.tripservice.services.proyeccion.ProyeccionReferenciasService;

/** Proyección de buses de Flota. Idempotente por versión. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorBusRegistrado implements ManejadorEvento<DatosBus> {

    private final ProyeccionReferenciasService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.BUS_REGISTRADO;
    }

    @Override
    public void manejar(SobreEvento<DatosBus> evento) {
        DatosBus d = evento.datos();
        proyeccion.aplicarBus(d, evento.versionAgregado());
        log.debug("BusRegistrado {} aplicado", d.idBus());
    }
}
