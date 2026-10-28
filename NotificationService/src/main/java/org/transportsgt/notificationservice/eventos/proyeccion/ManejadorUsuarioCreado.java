package org.transportsgt.notificationservice.eventos.proyeccion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.mensajeria.consumo.ManejadorEvento;
import org.transportsgt.eventos.contrato.SobreEvento;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.identidad.DatosUsuario;
import org.transportsgt.notificationservice.eventos.ValidacionEventos;
import org.transportsgt.notificationservice.services.proyeccion.ProyeccionContactosService;

/**
 * Cola {@code notificaciones.identidad.q}, llave {@code identidad.usuario.creado}.
 */
@Component
@RequiredArgsConstructor
public class ManejadorUsuarioCreado implements ManejadorEvento<DatosUsuario> {

    private final ProyeccionContactosService proyeccionContactos;
    private final ValidacionEventos validacion;

    @Override
    public TipoEvento tipo() {
        return TipoEvento.USUARIO_CREADO;
    }

    @Override
    public void manejar(SobreEvento<DatosUsuario> evento) {
        DatosUsuario d = validacion.datos(evento);
        validacion.requeridos(evento, "idUsuario", d.idUsuario(), "correo", d.correo(),
                "nombreCompleto", d.nombreCompleto(), "rol", d.rol());
        validacion.registrar(evento, proyeccionContactos.aplicar(d, evento.versionAgregado()));
    }
}
