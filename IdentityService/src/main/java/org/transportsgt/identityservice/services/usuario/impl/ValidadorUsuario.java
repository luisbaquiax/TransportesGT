package org.transportsgt.identityservice.services.usuario.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.identityservice.exception.CorreoDuplicadoException;
import org.transportsgt.identityservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.identityservice.exception.RolNoPermitidoException;
import org.transportsgt.identityservice.exception.SucursalInactivaException;
import org.transportsgt.identityservice.exception.SucursalNoPermitidaException;
import org.transportsgt.identityservice.models.Sucursal;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.UsuarioRepository;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/** Reglas de los datos de un usuario de personal, validadas antes de escribir (la base es la última defensa). */
@Component
@RequiredArgsConstructor
class ValidadorUsuario {

    /** Roles que pertenecen a una sucursal (ck_usuario_sucursal). */
    private static final Set<Rol> ROLES_CON_SUCURSAL = EnumSet.of(Rol.ADMIN_SUCURSAL, Rol.CAJERO, Rol.CHOFER);

    private final UsuarioRepository usuarioRepository;

    void rolDePersonal(Rol rol) {
        if (rol == Rol.CLIENTE) {
            throw new RolNoPermitidoException("Los clientes se registran por su cuenta en /auth/registro");
        }
    }

    /** Igual que ck_usuario_sucursal: sucursal obligatoria para roles de sucursal, prohibida para los demás. */
    void sucursalSegunRol(Rol rol, Sucursal sucursal) {
        if (ROLES_CON_SUCURSAL.contains(rol)) {
            if (sucursal == null) {
                throw new SucursalNoPermitidaException("El rol " + rol + " requiere una sucursal");
            }
            if (!Boolean.TRUE.equals(sucursal.getActiva())) {
                throw new SucursalInactivaException(sucursal.getId());
            }
        } else if (sucursal != null) {
            throw new SucursalNoPermitidaException("El rol " + rol + " no pertenece a una sucursal");
        }
    }

    /** @param correo ya normalizado */
    void correoDisponible(String correo) {
        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw new CorreoDuplicadoException(correo);
        }
    }

    void correoDisponibleParaOtro(String correo, Usuario usuario) {
        if (!correo.equals(usuario.getCorreo())
                && usuarioRepository.existsByCorreoIgnoreCaseAndIdNot(correo, usuario.getId())) {
            throw new CorreoDuplicadoException(correo);
        }
    }

    /**
     * El creador debe seguir activo (su JWT puede ser anterior a la desactivación). Para ADMIN_SUCURSAL debe ser
     * ADMIN_SISTEMA, como exige el trigger trg_admin_sucursal_creador.
     */
    void creador(UUID idCreador, Rol rolNuevo) {
        Usuario creador = usuarioRepository.findById(idCreador)
                .filter(u -> Boolean.TRUE.equals(u.getActivo()))
                .orElseThrow(() -> new OperacionNoAutorizadaException("El usuario creador no está activo"));
        if (rolNuevo == Rol.ADMIN_SUCURSAL && creador.getRol() != Rol.ADMIN_SISTEMA) {
            throw new OperacionNoAutorizadaException(
                    "Solo un administrador de sistema puede crear administradores de sucursal");
        }
    }
}
