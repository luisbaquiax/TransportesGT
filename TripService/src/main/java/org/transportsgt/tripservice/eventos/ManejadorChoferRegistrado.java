package org.transportsgt.tripservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.flota.DatosChofer;
import org.transportsgt.tripservice.services.proyeccion.ProyeccionReferenciasService;

/** Proyección de choferes de Flota. Idempotente por versión. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorChoferRegistrado implements ManejadorEvento<DatosChofer> {

    private final ProyeccionReferenciasService proyeccion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.CHOFER_REGISTRADO;
    }

    @Override
    public void manejar(SobreEvento<DatosChofer> evento) {
        DatosChofer d = evento.datos();
        proyeccion.aplicarChofer(d, evento.versionAgregado());
        log.debug("ChoferRegistrado {} aplicado", d.idChofer());
    }
}
