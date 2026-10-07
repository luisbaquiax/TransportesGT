package org.transportsgt.identityservice.config.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.identityservice.models.Usuario;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Usuario autenticado en el login. Guarda lo necesario para emitir el JWT
 * sin volver a consultar la base.
 */
public record UsuarioPrincipal(
        UUID id,
        String correo,
        String hashContrasena,
        String nombreCompleto,
        Rol rol,
        UUID idSucursal,
        boolean activo
) implements UserDetails {

    public static UsuarioPrincipal from(Usuario usuario) {
        return new UsuarioPrincipal(
                usuario.getId(),
                usuario.getCorreo(),
                usuario.getHashContrasena(),
                usuario.getNombreCompleto(),
                usuario.getRol(),
                // getId() sobre el proxy LAZY no dispara otra consulta
                usuario.getSucursal() != null ? usuario.getSucursal().getId() : null,
                Boolean.TRUE.equals(usuario.getActivo())
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + rol.name()));
    }

    @Override
    public String getPassword() {
        return hashContrasena;
    }

    @Override
    public String getUsername() {
        return correo;
    }

    /**
     * Un usuario desactivado no puede iniciar sesión (DisabledException).
     */
    @Override
    public boolean isEnabled() {
        return activo;
    }
}