package org.transportsgt.ticketservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.ticketservice.services.proyeccion.ProyeccionClientesService;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.cartera.DatosPerfilClienteActualizado;

@Component
@RequiredArgsConstructor
public class ManejadorPerfilClienteActualizado implements ManejadorEvento<DatosPerfilClienteActualizado> {

    private final ProyeccionClientesService proyeccionClientes;
    private final ValidacionEventos validacion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.PERFIL_CLIENTE_ACTUALIZADO;
    }

    @Override
    public void manejar(SobreEvento<DatosPerfilClienteActualizado> evento) {
        validacion.perfil(evento);
        validacion.registrar(evento, proyeccionClientes.aplicar(evento.datos(), evento.versionAgregado()));
    }
}
