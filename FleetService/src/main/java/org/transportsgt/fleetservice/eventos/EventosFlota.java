package org.transportsgt.fleetservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.RegistroOutbox;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.fleetservice.mappers.BusMapper;
import org.transportsgt.fleetservice.mappers.ChoferMapper;
import org.transportsgt.fleetservice.models.Bus;
import org.transportsgt.fleetservice.models.Chofer;

/**
 * Arma los payloads de flota y los guarda en {@code outbox_evento} con la librería de eventos.
 *
 * <p>Debe llamarse dentro de la transacción del cambio y DESPUÉS de {@code flush()}:
 * {@code versionAgregado} es la {@code version} de la entidad ya incrementada.
 *
 * <p>Flota NO publica BusActualizado por cambios de kilometraje: kilometraje_actual se actualiza
 * al consumir SalidaRegistrada / LlegadaRegistrada (catálogo §2.2).
 */
@Component
@RequiredArgsConstructor
public class EventosFlota {

    private final RegistroOutbox outbox;
    private final BusMapper busMapper;
    private final ChoferMapper choferMapper;

    public void busRegistrado(Bus bus) {
        outbox.registrar(TipoEvento.BUS_REGISTRADO, bus.getId(), bus.getVersion(), busMapper.toDatos(bus));
    }

    public void busActualizado(Bus bus) {
        outbox.registrar(TipoEvento.BUS_ACTUALIZADO, bus.getId(), bus.getVersion(), busMapper.toDatos(bus));
    }

    public void choferRegistrado(Chofer chofer) {
        outbox.registrar(TipoEvento.CHOFER_REGISTRADO, chofer.getId(), chofer.getVersion(),
                choferMapper.toDatos(chofer));
    }

    public void choferActualizado(Chofer chofer) {
        outbox.registrar(TipoEvento.CHOFER_ACTUALIZADO, chofer.getId(), chofer.getVersion(),
                choferMapper.toDatos(chofer));
    }
}
