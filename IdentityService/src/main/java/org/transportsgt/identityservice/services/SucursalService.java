package org.transportsgt.identityservice.services;

import org.springframework.data.domain.Pageable;
import org.transportsgt.identityservice.config.security.UsuarioAutenticado;
import org.transportsgt.identityservice.dto.request.ActualizarSucursalRequest;
import org.transportsgt.identityservice.dto.request.CrearSucursalRequest;
import org.transportsgt.identityservice.dto.response.PaginaResponse;
import org.transportsgt.identityservice.dto.response.SucursalCreadaResponse;
import org.transportsgt.identityservice.dto.response.SucursalResponse;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;
import org.transportsgt.identityservice.models.Sucursal;

import java.util.List;
import java.util.UUID;

public interface SucursalService {

    /**
     * Crea la sucursal y su primer ADMIN_SUCURSAL en una sola transacción.
     * Publica SucursalCreada, UsuarioCreado y EnlaceContrasenaEmitido(ACTIVACION).
     */
    SucursalCreadaResponse crear(CrearSucursalRequest request, UsuarioAutenticado autenticado, String ip);

    PaginaResponse<SucursalResponse> listar(Boolean activa, String nombre, Pageable pageable);

    SucursalResponse obtener(UUID id);

    /**
     * Publica SucursalActualizada.
     */
    SucursalResponse actualizar(UUID id, ActualizarSucursalRequest request);

    /**
     * Activa o desactiva; publica SucursalActualizada solo si el estado cambia.
     */
    SucursalResponse cambiarEstado(UUID id, boolean activa);

    List<UsuarioResponse> listarAdministradores(UUID id);

    /**
     * @throws org.transportsgt.identityservice.exception.SucursalNoEncontradaException si no existe
     */
    Sucursal buscar(UUID id);
}
