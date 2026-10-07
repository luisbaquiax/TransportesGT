package org.transportsgt.identityservice.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.identityservice.repositories.UsuarioRepository;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String correo) {
        return usuarioRepository.findByCorreoIgnoreCase(correo.trim())
                .map(UsuarioPrincipal::from)
                // Mismo mensaje que contraseña incorrecta: no revela si el correo existe
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));
    }
}