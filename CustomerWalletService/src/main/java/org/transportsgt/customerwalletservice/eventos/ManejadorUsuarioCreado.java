package org.transportsgt.customerwalletservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.customerwalletservice.services.ClienteService;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosUsuario;

/**
 * Cola {@code cartera.identidad.q}, llave {@code identidad.usuario.creado}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorUsuarioCreado implements ManejadorEvento<DatosUsuario> {

    private final ClienteService clienteService;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.USUARIO_CREADO;
    }

    @Override
    public void manejar(SobreEvento<DatosUsuario> evento) {
        ValidacionEventos.usuario(evento);
        ValidacionEventos.registrar(evento, clienteService.aplicarUsuario(evento.datos(), evento.versionAgregado()));
    }
}
