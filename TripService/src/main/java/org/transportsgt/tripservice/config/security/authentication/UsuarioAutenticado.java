package org.transportsgt.tripservice.config.security.authentication;

import org.transportsgt.eventos.payload.identidad.Rol;

import java.util.UUID;

/**
 * Usuario que hace la petición, tomado de los claims del JWT (no consulta a Identidad).
 * Lo construye {@link ConvertidorUsuarioAutenticado} a partir del {@code @AuthenticationPrincipal Jwt jwt}.
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

    public boolean perteneceASucursal(UUID idSucursalRecurso) {
        return idSucursal != null && idSucursal.equals(idSucursalRecurso);
    }
}
