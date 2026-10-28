package org.transportsgt.customerwalletservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.customerwalletservice.services.ClienteService;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.identidad.DatosUsuario;

/**
 * Cola {@code cartera.identidad.q}, llave {@code identidad.usuario.actualizado}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorUsuarioActualizado implements ManejadorEvento<DatosUsuario> {

    private final ClienteService clienteService;
    private final ValidacionEventos validacion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.USUARIO_ACTUALIZADO;
    }

    @Override
    public void manejar(SobreEvento<DatosUsuario> evento) {
        validacion.usuario(evento);
        validacion.registrar(evento, clienteService.aplicarUsuario(evento.datos(), evento.versionAgregado()));
    }
}
