package org.transportsgt.customerwalletservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.customerwalletservice.services.ReembolsoService;
import org.transportsgt.customerwalletservice.utils.Fechas;
import org.transportsgt.eventos.EventoNoRecuperableException;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosAlquilerCancelado;
import org.transportsgt.eventos.payload.TipoReferencia;

import java.time.Instant;

/**
 * Cola {@code cartera.alquileres.q}, llave {@code alquileres.alquiler.cancelado}. Solo se reembolsa si se pagó con
 * cartera ({@code idPago != null}). Fecha del reembolso: {@code canceladoEn} (o {@code ocurridoEn} si no viene).
 */
@Component
@RequiredArgsConstructor
public class ManejadorAlquilerCancelado implements ManejadorEvento<DatosAlquilerCancelado> {

    private final ReembolsoService reembolsoService;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.ALQUILER_CANCELADO;
    }

    @Override
    public void manejar(SobreEvento<DatosAlquilerCancelado> evento) {
        DatosAlquilerCancelado datos = evento.datos();
        Instant cancelado = datos.canceladoEn() != null ? datos.canceladoEn() : evento.ocurridoEn();
        if (datos.idAlquiler() == null || cancelado == null) {
            throw new EventoNoRecuperableException("El evento " + evento.idEvento()
                    + " no trae idAlquiler o la fecha de cancelación");
        }
        if (datos.idPago() == null) {
            ValidacionEventos.registrar(evento, false);
            return;
        }
        boolean aplicado = reembolsoService.reembolsar(TipoReferencia.ALQUILER, datos.idAlquiler(), datos.idPago(),
                Fechas.enGuatemala(cancelado), datos.motivo());
        ValidacionEventos.registrar(evento, aplicado);
    }
}
