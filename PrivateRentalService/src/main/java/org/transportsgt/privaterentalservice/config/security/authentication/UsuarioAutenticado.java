package org.transportsgt.privaterentalservice.config.security.authentication;

import org.transportsgt.eventos.payload.identidad.Rol;

import java.util.UUID;

/**
 * Usuario que hace la petición, tomado de los claims del JWT (no consulta a Identidad).
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

    /**
     * Reglas tipo "el administrador solo gestiona lo de SU sucursal".
     */
    public boolean perteneceASucursal(UUID idSucursalRecurso) {
        return idSucursal != null && idSucursal.equals(idSucursalRecurso);
    }
}
