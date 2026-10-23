package org.transportsgt.privaterentalservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.ManejadorEvento;
import org.transportsgt.eventos.SobreEvento;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosPerfilClienteActualizado;
import org.transportsgt.privaterentalservice.services.proyeccion.ProyeccionReferenciasService;

/** Perfil comercial del cliente (Cartera): NIT y nombre para la solicitud en línea y el historial por NIT. */
@Component
@RequiredArgsConstructor
public class ManejadorPerfilClienteActualizado implements ManejadorEvento<DatosPerfilClienteActualizado> {

    private final ProyeccionReferenciasService servicio;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.PERFIL_CLIENTE_ACTUALIZADO;
    }

    @Override
    public void manejar(SobreEvento<DatosPerfilClienteActualizado> evento) {
        servicio.aplicarCliente(evento.datos(), evento.versionAgregado());
    }
}
