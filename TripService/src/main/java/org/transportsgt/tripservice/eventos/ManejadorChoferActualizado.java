package org.transportsgt.tripservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosChofer;
import org.transportsgt.tripservice.services.proyeccion.ProyeccionReferenciasService;

/** Proyección de choferes de Flota. Idempotente por versión. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorChoferActualizado implements ManejadorEvento<DatosChofer> {

    private final ProyeccionReferenciasService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.CHOFER_ACTUALIZADO;
    }

    @Override
    public void manejar(SobreEvento<DatosChofer> evento) {
        DatosChofer d = evento.datos();
        proyeccion.aplicarChofer(d, evento.versionAgregado());
        log.debug("ChoferActualizado {} aplicado", d.idChofer());
    }
}
