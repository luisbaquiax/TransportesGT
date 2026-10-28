package org.transportsgt.identityservice.services.contrasena.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.identidad.MotivoContrasena;
import org.transportsgt.eventos.payload.identidad.PropositoContrasena;
import org.transportsgt.identityservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.identityservice.dto.request.CambiarContrasenaRequest;
import org.transportsgt.identityservice.dto.request.OlvideContrasenaRequest;
import org.transportsgt.identityservice.dto.request.RestablecerContrasenaRequest;
import org.transportsgt.identityservice.dto.response.EnlaceContrasenaResponse;
import org.transportsgt.identityservice.eventos.EventosIdentidad;
import org.transportsgt.identityservice.exception.UsuarioInactivoException;
import org.transportsgt.identityservice.models.TokenContrasena;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.TokenContrasenaRepository;
import org.transportsgt.identityservice.repositories.UsuarioRepository;
import org.transportsgt.identityservice.services.comun.BuscadorIdentidad;
import org.transportsgt.identityservice.services.contrasena.ContrasenaService;
import org.transportsgt.identityservice.services.contrasena.EnlacesContrasenaService;
import org.transportsgt.identityservice.utils.NormalizadorCorreo;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContrasenaServiceImpl implements ContrasenaService {

    private final UsuarioRepository usuarioRepository;
    private final TokenContrasenaRepository tokenRepository;
    private final EnlacesContrasenaService enlaces;
    private final ValidadorContrasena validador;
    private final BuscadorIdentidad buscador;
    private final PasswordEncoder passwordEncoder;
    private final NormalizadorCorreo correos;
    private final EventosIdentidad eventos;
    private final Clock clock;

    @Override
    @Transactional
    public void cambiarContrasena(CambiarContrasenaRequest request, UsuarioAutenticado autenticado) {
        Usuario usuario = buscador.usuario(autenticado.id());
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            throw new UsuarioInactivoException();
        }
        validador.verificarActual(usuario, request.contrasenaActual());
        validador.verificarDistinta(usuario, request.nuevaContrasena());
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        usuario.setHashContrasena(passwordEncoder.encode(request.nuevaContrasena()));
        usuario.setContrasenaCambiadaEn(ahora);

        Usuario guardado = usuarioRepository.saveAndFlush(usuario);
        // Un enlace pendiente ya no tiene sentido después de cambiar la contraseña
        tokenRepository.invalidarTodosActivos(guardado.getId(), ahora);
        eventos.contrasenaCambiada(guardado, MotivoContrasena.CAMBIO, ahora.toInstant());
    }

    @Override
    @Transactional
    public void olvideContrasena(OlvideContrasenaRequest request, String ip) {
        Optional<Usuario> usuario = usuarioRepository.findByCorreoIgnoreCase(correos.normalizar(request.correo()))
                .filter(u -> Boolean.TRUE.equals(u.getActivo()));
        if (usuario.isEmpty()) {
            log.debug("Olvidé mi contraseña: correo inexistente o usuario inactivo; no se emite enlace");
            return;
        }
        if (enlaces.superaLimiteRestablecimiento(usuario.get().getId())) {
            log.info("Usuario {} superó el límite de solicitudes de restablecimiento por hora", usuario.get().getId());
            return;
        }
        enlaces.emitir(usuario.get(), PropositoContrasena.RESTABLECIMIENTO, ip);
    }

    @Override
    @Transactional(readOnly = true)
    public EnlaceContrasenaResponse validarEnlace(String token) {
        TokenContrasena vigente = enlaces.buscarVigente(token);
        return new EnlaceContrasenaResponse(vigente.getProposito(),
                correos.enmascarar(vigente.getUsuario().getCorreo()));
    }

    @Override
    @Transactional
    public void restablecerContrasena(RestablecerContrasenaRequest request) {
        TokenContrasena vigente = enlaces.buscarVigente(request.token());
        Usuario usuario = vigente.getUsuario();
        validador.verificarDistinta(usuario, request.nuevaContrasena());
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        vigente.setUsadoEn(ahora);
        usuario.setHashContrasena(passwordEncoder.encode(request.nuevaContrasena()));
        usuario.setContrasenaCambiadaEn(ahora);

        // El flush también guarda usado_en del token (está en el mismo contexto de persistencia)
        Usuario guardado = usuarioRepository.saveAndFlush(usuario);
        tokenRepository.invalidarTodosActivos(guardado.getId(), ahora);
        eventos.contrasenaCambiada(guardado, validador.motivoDe(vigente.getProposito()), ahora.toInstant());
    }
}
