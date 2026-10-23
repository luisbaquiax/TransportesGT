package org.transportsgt.identityservice.services.sucursal;

import org.transportsgt.identityservice.config.security.UsuarioAutenticado;
import org.transportsgt.identityservice.dto.request.ActualizarSucursalRequest;
import org.transportsgt.identityservice.dto.request.CrearSucursalRequest;
import org.transportsgt.identityservice.dto.response.SucursalCreadaResponse;
import org.transportsgt.identityservice.dto.response.SucursalResponse;

import java.util.UUID;

/** Cambios sobre las sucursales (solo ADMIN_SISTEMA; nunca se eliminan). */
public interface GestionSucursalesService {

    /**
     * Crea la sucursal y su primer ADMIN_SUCURSAL en una sola transacción.
     * Publica SucursalCreada, UsuarioCreado y EnlaceContrasenaEmitido(ACTIVACION).
     */
    SucursalCreadaResponse crear(CrearSucursalRequest request, UsuarioAutenticado autenticado, String ip);

    /** Publica SucursalActualizada. */
    SucursalResponse actualizar(UUID id, ActualizarSucursalRequest request);

    /** Activa o desactiva; publica SucursalActualizada solo si el estado cambia. */
    SucursalResponse cambiarEstado(UUID id, boolean activa);
}
