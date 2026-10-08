package org.transportsgt.identityservice.config.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.identityservice.models.Usuario;

import java.util.UUID;

/**
 * Usuario que hace la petición, tomado de los claims del JWT (no consulta la base).
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

    public static UsuarioAutenticado de(Usuario usuario) {
        return new UsuarioAutenticado(
                usuario.getId(),
                usuario.getCorreo(),
                usuario.getRol(),
                usuario.getSucursal() != null ? usuario.getSucursal().getId() : null,
                usuario.getNombreCompleto());
    }

    public boolean esAdminSistema() {
        return rol == Rol.ADMIN_SISTEMA;
    }

    public boolean esAdminSucursal() {
        return rol == Rol.ADMIN_SUCURSAL;
    }
}
