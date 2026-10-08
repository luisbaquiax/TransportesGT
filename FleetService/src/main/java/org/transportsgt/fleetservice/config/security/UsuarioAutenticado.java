package org.transportsgt.fleetservice.config.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.transportsgt.eventos.payload.Rol;

import java.util.UUID;

/**
 * Usuario que hace la petición, tomado de los claims del JWT (no consulta a Identidad).
 * En los controladores: {@code UsuarioAutenticado.de(jwt)} con {@code @AuthenticationPrincipal Jwt jwt}.
 */
public record UsuarioAutenticado(
        UUID id,
        String correo,
        Rol rol,
        UUID idSucursal,
        String nombre
) {

    public static UsuarioAutenticado de(Jwt jwt) {
        String idSucursal = jwt.getClaimAsString("idSucursal");
        return new UsuarioAutenticado(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("correo"),
                Rol.valueOf(jwt.getClaimAsString("rol")),
                idSucursal == null ? null : UUID.fromString(idSucursal),
                jwt.getClaimAsString("nombre"));
    }

    public boolean esAdminSistema() {
        return rol == Rol.ADMIN_SISTEMA;
    }

    public boolean esAdminSucursal() {
        return rol == Rol.ADMIN_SUCURSAL;
    }

    /** Reglas tipo "el administrador solo gestiona lo de SU sucursal". */
    public boolean perteneceASucursal(UUID idSucursalRecurso) {
        return idSucursal != null && idSucursal.equals(idSucursalRecurso);
    }
}
