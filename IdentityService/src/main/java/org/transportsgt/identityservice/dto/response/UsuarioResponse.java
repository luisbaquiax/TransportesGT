package org.transportsgt.identityservice.dto.response;

import org.transportsgt.eventos.payload.Rol;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * @param activacionPendiente true si el usuario aún no definió su contraseña con el enlace de activación
 */
public record UsuarioResponse(
        UUID id,
        String correo,
        String nombreCompleto,
        Rol rol,
        UUID idSucursal,
        String nombreSucursal,
        boolean activo,
        boolean activacionPendiente,
        Long version,
        OffsetDateTime creadoEn
) {
}
