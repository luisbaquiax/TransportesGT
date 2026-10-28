package org.transportsgt.identityservice.config.security.authentication;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.UsuarioRepository;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String correo) {
        return usuarioRepository.findByCorreoIgnoreCase(correo.trim())
                .map(this::aPrincipal)
                // Mismo mensaje que contraseña incorrecta: no revela si el correo existe
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));
    }

    /**
     * Lo necesario para emitir el JWT sin volver a consultar la base.
     */
    private UsuarioPrincipal aPrincipal(Usuario usuario) {
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
}
