package org.transportsgt.identityservice.dto.response;

import org.transportsgt.eventos.payload.Rol;

import java.util.UUID;

/**
 * Datos del usuario que devuelve el login (los mismos que van en el JWT).
 */
public record UsuarioSesionResponse(
        UUID id,
        String correo,
        String nombreCompleto,
        Rol rol,
        UUID idSucursal
) {
}
