package org.transportsgt.identityservice.config.security;

import org.transportsgt.eventos.payload.Rol;

import java.util.UUID;

/**
 * Usuario que hace la petición, tomado de los claims del JWT.
 * En los controladores: {@link ConvertidorUsuarioAutenticado} con {@code @AuthenticationPrincipal Jwt jwt}.
 */
public record UsuarioAutenticado(
        UUID id,
        String correo,
        Rol rol,
        UUID idSucursal,
        String nombre
) {

    public boolean esAdminSistema() {
        return rol == Rol.ADMIN_SISTEMA;
    }

    public boolean esAdminSucursal() {
        return rol == Rol.ADMIN_SUCURSAL;
    }
}
