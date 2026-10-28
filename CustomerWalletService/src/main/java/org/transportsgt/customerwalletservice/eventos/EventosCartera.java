package org.transportsgt.customerwalletservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.customerwalletservice.models.Cartera;
import org.transportsgt.customerwalletservice.models.Cliente;
import org.transportsgt.customerwalletservice.models.MovimientoCartera;
import org.transportsgt.eventos.mensajeria.publicacion.RegistroOutbox;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.cartera.DatosCarteraRecargada;
import org.transportsgt.eventos.payload.cartera.DatosCarteraReembolsada;
import org.transportsgt.eventos.payload.cartera.DatosPerfilClienteActualizado;

/**
 * Arma los payloads de Clientes y Cartera y los guarda en {@code outbox_evento} con la librería de eventos.
 *
 */
@Component
@RequiredArgsConstructor
public class EventosCartera {

    private final RegistroOutbox outbox;

    /**
     * Instantánea del perfil comercial: {@code idAgregado = id_usuario}, versión del cliente.
     */
    public void perfilClienteActualizado(Cliente cliente) {
        DatosPerfilClienteActualizado datos = new DatosPerfilClienteActualizado(cliente.getIdUsuario(),
                cliente.getNit(), cliente.getNombreCompleto(), cliente.getActivo());
        outbox.registrar(TipoEvento.PERFIL_CLIENTE_ACTUALIZADO, cliente.getIdUsuario(), cliente.getVersion(), datos);
    }

    public void carteraRecargada(Cartera cartera, MovimientoCartera recarga) {
        DatosCarteraRecargada datos = new DatosCarteraRecargada(recarga.getId(), cartera.getId(),
                cartera.getCliente().getIdUsuario(), recarga.getMonto(), recarga.getSaldoPosterior(),
                recarga.getFechaMovimiento());
        outbox.registrar(TipoEvento.CARTERA_RECARGADA, cartera.getId(), cartera.getVersion(), datos);
    }

    public void carteraReembolsada(Cartera cartera, MovimientoCartera reembolso, String motivo) {
        DatosCarteraReembolsada datos = new DatosCarteraReembolsada(reembolso.getId(), cartera.getId(),
                cartera.getCliente().getIdUsuario(), reembolso.getMonto(), reembolso.getSaldoPosterior(),
                reembolso.getTipoReferencia(), reembolso.getIdReferencia(), motivo);
        outbox.registrar(TipoEvento.CARTERA_REEMBOLSADA, cartera.getId(), cartera.getVersion(), datos);
    }
}
