package org.transportsgt.identityservice.services;

import org.springframework.data.domain.Pageable;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.identityservice.config.security.UsuarioAutenticado;
import org.transportsgt.identityservice.dto.request.ActualizarPerfilRequest;
import org.transportsgt.identityservice.dto.request.ActualizarUsuarioRequest;
import org.transportsgt.identityservice.dto.request.CrearUsuarioRequest;
import org.transportsgt.identityservice.dto.response.PaginaResponse;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;
import org.transportsgt.identityservice.models.Sucursal;
import org.transportsgt.identityservice.models.Usuario;

import java.util.UUID;

public interface UsuarioService {

    /**
     * Alta de personal (ADMIN_SISTEMA, ADMIN_SUCURSAL, CAJERO, CHOFER) con contraseña aleatoria y enlace
     * de ACTIVACION. Valida correo, sucursal según el rol y que el creador esté activo (y sea ADMIN_SISTEMA
     * si el nuevo es ADMIN_SUCURSAL). Publica UsuarioCreado y EnlaceContrasenaEmitido.
     * Debe llamarse dentro de una transacción.
     */
    Usuario registrarPersonal(String nombreCompleto, String correo, Rol rol, Sucursal sucursal,
                              UUID idCreador, String ip);

    /**
     * POST /usuarios: valida qué rol puede crear el autenticado y delega en {@link #registrarPersonal}.
     */
    UsuarioResponse crear(CrearUsuarioRequest request, UsuarioAutenticado autenticado, String ip);

    /**
     * ADMIN_SUCURSAL solo ve los usuarios de su sucursal.
     */
    PaginaResponse<UsuarioResponse> listar(Rol rol, UUID idSucursal, Boolean activo, String texto,
                                           Pageable pageable, UsuarioAutenticado autenticado);

    UsuarioResponse obtener(UUID id, UsuarioAutenticado autenticado);

    /**
     * Cambia nombre y correo. Publica UsuarioActualizado.
     */
    UsuarioResponse actualizar(UUID id, ActualizarUsuarioRequest request, UsuarioAutenticado autenticado);

    /**
     * Activa o desactiva (nunca se elimina). Valida "último ADMIN_SISTEMA" y "último ADMIN_SUCURSAL de la
     * sucursal". Publica UsuarioActualizado solo si el estado cambia.
     */
    UsuarioResponse cambiarEstado(UUID id, boolean activo, UsuarioAutenticado autenticado);

    /**
     * Emite un nuevo enlace de ACTIVACION (invalida el anterior).
     */
    void reenviarActivacion(UUID id, UsuarioAutenticado autenticado, String ip);

    /**
     * PUT /usuarios/yo: el usuario cambia su propio nombre. Publica UsuarioActualizado.
     */
    UsuarioResponse actualizarPerfil(ActualizarPerfilRequest request, UsuarioAutenticado autenticado);

    /**
     * @throws org.transportsgt.identityservice.exception.UsuarioNoEncontradoException si no existe
     */
    Usuario buscar(UUID id);
}
