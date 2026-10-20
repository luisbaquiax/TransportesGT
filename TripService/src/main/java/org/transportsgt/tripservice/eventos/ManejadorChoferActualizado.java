package org.transportsgt.tripservice.eventos;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosChofer;
import org.transportsgt.tripservice.repositories.ChoferRefRepository;

/** Proyección de choferes de Flota. Idempotente por versión. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManejadorChoferActualizado implements ManejadorEvento<DatosChofer> {

    private final ChoferRefRepository repository;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.CHOFER_ACTUALIZADO;
    }

    @Override
    public void manejar(SobreEvento<DatosChofer> evento) {
        DatosChofer d = evento.datos();
        long version = evento.versionAgregado() == null ? 0L : evento.versionAgregado();
        repository.upsert(d.idChofer(), d.idUsuario(), d.idSucursal(), d.nombreCompleto(),
                d.salarioBaseViaje(), d.fechaVencimientoLicencia(), d.activo(), version);
        log.debug("ChoferActualizado {} aplicado", d.idChofer());
    }
}
