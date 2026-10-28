package org.transportsgt.privaterentalservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.publicacion.RegistroOutbox;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.privaterentalservice.mappers.AlquilerMapper;
import org.transportsgt.privaterentalservice.models.Alquiler;

import java.util.UUID;

/** Guarda los eventos de alquiler en el outbox (misma transacción que el cambio). */
@Component
@RequiredArgsConstructor
public class EventosAlquiler {

    private final RegistroOutbox outbox;
    private final AlquilerMapper mapper;

    public void alquilerSolicitado(Alquiler a) {
        outbox.registrar(TipoEvento.ALQUILER_SOLICITADO, a.getId(), a.getVersion(),
                mapper.toDatosSolicitado(a));
    }

    public void alquilerCotizado(Alquiler a, UUID idUsuarioCotizador) {
        outbox.registrar(TipoEvento.ALQUILER_COTIZADO, a.getId(), a.getVersion(),
                mapper.toDatosCotizado(a, idUsuarioCotizador));
    }

    public void alquilerConfirmado(Alquiler a) {
        outbox.registrar(TipoEvento.ALQUILER_CONFIRMADO, a.getId(), a.getVersion(),
                mapper.toDatosConfirmado(a));
    }

    public void alquilerCancelado(Alquiler a, String motivo) {
        outbox.registrar(TipoEvento.ALQUILER_CANCELADO, a.getId(), a.getVersion(),
                mapper.toDatosCancelado(a, motivo));
    }
}
