package org.transportsgt.identityservice.services.usuario;

import org.transportsgt.identityservice.config.security.UsuarioAutenticado;
import org.transportsgt.identityservice.dto.request.ActualizarPerfilRequest;
import org.transportsgt.identityservice.dto.request.ActualizarUsuarioRequest;
import org.transportsgt.identityservice.dto.request.CrearUsuarioRequest;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;

import java.util.UUID;

/** Cambios sobre los usuarios (nunca se eliminan). Quién puede qué lo decide AutorizacionUsuarios. */
public interface GestionUsuariosService {

    /** POST /usuarios: valida qué rol puede crear el autenticado y delega en AltaPersonalService. */
    UsuarioResponse crear(CrearUsuarioRequest request, UsuarioAutenticado autenticado, String ip);

    /** Cambia nombre y correo. Publica UsuarioActualizado. */
    UsuarioResponse actualizar(UUID id, ActualizarUsuarioRequest request, UsuarioAutenticado autenticado);

    /**
     * Activa o desactiva. Valida "último ADMIN_SISTEMA" y "último ADMIN_SUCURSAL de la sucursal".
     * Publica UsuarioActualizado solo si el estado cambia.
     */
    UsuarioResponse cambiarEstado(UUID id, boolean activo, UsuarioAutenticado autenticado);

    /** Emite un nuevo enlace de ACTIVACION (invalida el anterior). */
    void reenviarActivacion(UUID id, UsuarioAutenticado autenticado, String ip);

    /** PUT /usuarios/yo: el usuario cambia su propio nombre. Publica UsuarioActualizado. */
    UsuarioResponse actualizarPerfil(ActualizarPerfilRequest request, UsuarioAutenticado autenticado);
}
