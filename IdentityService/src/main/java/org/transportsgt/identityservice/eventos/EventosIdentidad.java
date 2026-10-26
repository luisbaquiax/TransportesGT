package org.transportsgt.identityservice.eventos;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.RegistroOutbox;
import org.transportsgt.eventos.TipoEvento;
import org.transportsgt.eventos.payload.DatosContrasenaCambiada;
import org.transportsgt.eventos.payload.DatosEnlaceContrasena;
import org.transportsgt.eventos.payload.MotivoContrasena;
import org.transportsgt.eventos.payload.PropositoContrasena;
import org.transportsgt.identityservice.mappers.SucursalMapper;
import org.transportsgt.identityservice.mappers.UsuarioMapper;
import org.transportsgt.identityservice.models.Sucursal;
import org.transportsgt.identityservice.models.Usuario;

import java.time.Instant;

/**
 * Arma los payloads de identidad y los guarda en {@code outbox_evento} con la librería de eventos.
 *
 * <p>Debe llamarse dentro de la transacción del cambio y DESPUÉS de {@code flush()}:
 * {@code versionAgregado} es la {@code version} de la entidad ya incrementada.
 */
@Component
@RequiredArgsConstructor
public class EventosIdentidad {

    private final RegistroOutbox outbox;
    private final SucursalMapper sucursalMapper;
    private final UsuarioMapper usuarioMapper;

    public void sucursalCreada(Sucursal sucursal) {
        outbox.registrar(TipoEvento.SUCURSAL_CREADA, sucursal.getId(), sucursal.getVersion(),
                sucursalMapper.toDatos(sucursal));
    }

    public void sucursalActualizada(Sucursal sucursal) {
        outbox.registrar(TipoEvento.SUCURSAL_ACTUALIZADA, sucursal.getId(), sucursal.getVersion(),
                sucursalMapper.toDatos(sucursal));
    }

    public void usuarioCreado(Usuario usuario) {
        outbox.registrar(TipoEvento.USUARIO_CREADO, usuario.getId(), usuario.getVersion(),
                usuarioMapper.toDatos(usuario));
    }

    public void usuarioActualizado(Usuario usuario) {
        outbox.registrar(TipoEvento.USUARIO_ACTUALIZADO, usuario.getId(), usuario.getVersion(),
                usuarioMapper.toDatos(usuario));
    }

    /**
     * El payload lleva el token en claro: solo viaja en este evento (Notificaciones arma el correo).
     */
    public void enlaceContrasenaEmitido(Usuario usuario, PropositoContrasena proposito, String token,
                                        Instant expiraEn) {
        DatosEnlaceContrasena datos = new DatosEnlaceContrasena(usuario.getId(), usuario.getCorreo(),
                usuario.getNombreCompleto(), proposito, token, expiraEn);
        outbox.registrar(TipoEvento.ENLACE_CONTRASENA_EMITIDO, usuario.getId(), usuario.getVersion(), datos);
    }

    public void contrasenaCambiada(Usuario usuario, MotivoContrasena motivo, Instant cambiadaEn) {
        DatosContrasenaCambiada datos = new DatosContrasenaCambiada(usuario.getId(), usuario.getCorreo(),
                usuario.getNombreCompleto(), motivo, cambiadaEn);
        outbox.registrar(TipoEvento.CONTRASENA_CAMBIADA, usuario.getId(), usuario.getVersion(), datos);
    }
}
