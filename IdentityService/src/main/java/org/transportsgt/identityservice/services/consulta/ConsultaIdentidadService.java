package org.transportsgt.identityservice.services.consulta;

import org.springframework.data.domain.Pageable;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.identityservice.config.security.UsuarioAutenticado;
import org.transportsgt.identityservice.dto.response.PaginaResponse;
import org.transportsgt.identityservice.dto.response.SucursalResponse;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;

import java.util.List;
import java.util.UUID;

/**
 * Lectura de usuarios y sucursales.
 */
public interface ConsultaIdentidadService {

    /**
     * ADMIN_SUCURSAL solo ve los usuarios de su sucursal.
     */
    PaginaResponse<UsuarioResponse> listarUsuarios(Rol rol, UUID idSucursal, Boolean activo, String texto,
                                                   Pageable pageable, UsuarioAutenticado autenticado);

    UsuarioResponse obtenerUsuario(UUID id, UsuarioAutenticado autenticado);

    PaginaResponse<SucursalResponse> listarSucursales(Boolean activa, String nombre, Pageable pageable);

    SucursalResponse obtenerSucursal(UUID id);

    List<UsuarioResponse> listarAdministradores(UUID idSucursal);
}
