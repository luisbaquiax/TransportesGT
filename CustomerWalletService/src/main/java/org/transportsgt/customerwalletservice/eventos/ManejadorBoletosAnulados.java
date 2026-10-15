package org.transportsgt.customerwalletservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.customerwalletservice.services.ReembolsoService;
import org.transportsgt.customerwalletservice.utils.Fechas;
import org.transportsgt.eventos.EventoNoRecuperableException;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.Canal;
import org.transportsgt.eventos.payload.DatosBoletosAnulados;
import org.transportsgt.eventos.payload.TipoReferencia;

/**
 * Cola {@code cartera.boletos.q}, llave {@code boletos.venta.anulada}. Solo se reembolsan las compras EN_LINEA:
 * las de mostrador solo revierten el ingreso (lo hace Reportes). Fecha del reembolso: {@code ocurridoEn} del sobre.
 */
@Component
@RequiredArgsConstructor
public class ManejadorBoletosAnulados implements ManejadorEvento<DatosBoletosAnulados> {

    private final ReembolsoService reembolsoService;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.BOLETOS_ANULADOS;
    }

    @Override
    public void manejar(SobreEvento<DatosBoletosAnulados> evento) {
        DatosBoletosAnulados datos = evento.datos();
        if (datos.idCompra() == null || evento.ocurridoEn() == null) {
            throw new EventoNoRecuperableException("El evento " + evento.idEvento() + " no trae idCompra u ocurridoEn");
        }
        if (datos.canal() != Canal.EN_LINEA) {
            ValidacionEventos.registrar(evento, false);
            return;
        }
        boolean aplicado = reembolsoService.reembolsar(TipoReferencia.BOLETOS, datos.idCompra(), datos.idPago(),
                Fechas.enGuatemala(evento.ocurridoEn()), datos.motivo());
        ValidacionEventos.registrar(evento, aplicado);
    }
}
