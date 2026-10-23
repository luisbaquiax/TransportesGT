package org.transportsgt.identityservice.services.registro.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.identityservice.dto.request.RegistroRequest;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;
import org.transportsgt.identityservice.eventos.EventosIdentidad;
import org.transportsgt.identityservice.exception.CorreoDuplicadoException;
import org.transportsgt.identityservice.mappers.UsuarioMapper;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.UsuarioRepository;
import org.transportsgt.identityservice.services.registro.RegistroClienteService;
import org.transportsgt.identityservice.utils.NormalizadorCorreo;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class RegistroClienteServiceImpl implements RegistroClienteService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final NormalizadorCorreo correos;
    private final UsuarioMapper usuarioMapper;
    private final EventosIdentidad eventos;
    private final Clock clock;

    @Override
    @Transactional
    public UsuarioResponse registrar(RegistroRequest request) {
        String correo = correos.normalizar(request.correo());
        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw new CorreoDuplicadoException(correo);
        }
        Usuario usuario = Usuario.builder()
                .nombreCompleto(request.nombreCompleto().trim())
                .correo(correo)
                .hashContrasena(passwordEncoder.encode(request.contrasena()))
                .rol(Rol.CLIENTE)
                .activo(true)
                .contrasenaCambiadaEn(OffsetDateTime.now(clock))
                .build();

        Usuario guardado = usuarioRepository.saveAndFlush(usuario);
        eventos.usuarioCreado(guardado);
        return usuarioMapper.toResponse(guardado);
    }
}
