package org.transportsgt.identityservice.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.transportsgt.eventos.payload.MotivoContrasena;
import org.transportsgt.eventos.payload.PropositoContrasena;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.identityservice.config.security.JwtService;
import org.transportsgt.identityservice.config.security.UsuarioAutenticado;
import org.transportsgt.identityservice.config.security.UsuarioPrincipal;
import org.transportsgt.identityservice.dto.request.CambiarContrasenaRequest;
import org.transportsgt.identityservice.dto.request.LoginRequest;
import org.transportsgt.identityservice.dto.request.OlvideContrasenaRequest;
import org.transportsgt.identityservice.dto.request.RegistroRequest;
import org.transportsgt.identityservice.dto.request.RestablecerContrasenaRequest;
import org.transportsgt.identityservice.dto.response.EnlaceContrasenaResponse;
import org.transportsgt.identityservice.dto.response.LoginResponse;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;
import org.transportsgt.identityservice.dto.response.UsuarioSesionResponse;
import org.transportsgt.identityservice.eventos.EventosIdentidad;
import org.transportsgt.identityservice.exception.ContrasenaActualIncorrectaException;
import org.transportsgt.identityservice.exception.ContrasenaRepetidaException;
import org.transportsgt.identityservice.exception.CorreoDuplicadoException;
import org.transportsgt.identityservice.exception.CredencialesInvalidasException;
import org.transportsgt.identityservice.exception.UsuarioInactivoException;
import org.transportsgt.identityservice.exception.UsuarioNoEncontradoException;
import org.transportsgt.identityservice.mappers.UsuarioMapper;
import org.transportsgt.identityservice.models.TokenContrasena;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.TokenContrasenaRepository;
import org.transportsgt.identityservice.repositories.UsuarioRepository;
import org.transportsgt.identityservice.services.AuthService;
import org.transportsgt.identityservice.services.TokenContrasenaService;
import org.transportsgt.identityservice.utils.Correos;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final TokenContrasenaRepository tokenRepository;
    private final TokenContrasenaService tokenContrasenaService;
    private final PasswordEncoder passwordEncoder;
    private final UsuarioMapper usuarioMapper;
    private final EventosIdentidad eventos;
    private final Clock clock;

    @Override
    public LoginResponse login(LoginRequest request) {
        Authentication autenticacion;
        try {
            autenticacion = authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(
                    Correos.normalizar(request.correo()), request.contrasena()));
        } catch (BadCredentialsException e) {
            throw new CredencialesInvalidasException();
        } catch (DisabledException e) {
            // Solo llega aquí con la contraseña correcta (ver SecurityConfig.authenticationManager)
            throw new UsuarioInactivoException();
        }
        UsuarioPrincipal principal = (UsuarioPrincipal) autenticacion.getPrincipal();
        JwtService.TokenEmitido token = jwtService.generarToken(principal);
        UsuarioSesionResponse usuario = new UsuarioSesionResponse(principal.id(), principal.correo(),
                principal.nombreCompleto(), principal.rol(), principal.idSucursal());
        return new LoginResponse(token.token(), token.expiraEn(), usuario);
    }

    @Override
    @Transactional
    public UsuarioResponse registrar(RegistroRequest request) {
        String correo = Correos.normalizar(request.correo());
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

    @Override
    @Transactional(readOnly = true)
    public UsuarioResponse miPerfil(UsuarioAutenticado autenticado) {
        return usuarioMapper.toResponse(buscarUsuario(autenticado));
    }

    @Override
    @Transactional
    public void cambiarContrasena(CambiarContrasenaRequest request, UsuarioAutenticado autenticado) {
        Usuario usuario = buscarUsuario(autenticado);
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            throw new UsuarioInactivoException();
        }
        if (!passwordEncoder.matches(request.contrasenaActual(), usuario.getHashContrasena())) {
            throw new ContrasenaActualIncorrectaException();
        }
        if (passwordEncoder.matches(request.nuevaContrasena(), usuario.getHashContrasena())) {
            throw new ContrasenaRepetidaException();
        }
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
        Optional<Usuario> usuario = usuarioRepository.findByCorreoIgnoreCase(Correos.normalizar(request.correo()))
                .filter(u -> Boolean.TRUE.equals(u.getActivo()));
        if (usuario.isEmpty()) {
            log.debug("Olvidé mi contraseña: correo inexistente o usuario inactivo; no se emite enlace");
            return;
        }
        if (tokenContrasenaService.superaLimiteRestablecimiento(usuario.get().getId())) {
            log.info("Usuario {} superó el límite de solicitudes de restablecimiento por hora", usuario.get().getId());
            return;
        }
        tokenContrasenaService.emitir(usuario.get(), PropositoContrasena.RESTABLECIMIENTO, ip);
    }

    @Override
    @Transactional(readOnly = true)
    public EnlaceContrasenaResponse validarEnlace(String token) {
        TokenContrasena vigente = tokenContrasenaService.buscarVigente(token);
        return new EnlaceContrasenaResponse(vigente.getProposito(),
                Correos.enmascarar(vigente.getUsuario().getCorreo()));
    }

    @Override
    @Transactional
    public void restablecerContrasena(RestablecerContrasenaRequest request) {
        TokenContrasena vigente = tokenContrasenaService.buscarVigente(request.token());
        Usuario usuario = vigente.getUsuario();
        if (passwordEncoder.matches(request.nuevaContrasena(), usuario.getHashContrasena())) {
            throw new ContrasenaRepetidaException();
        }
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        vigente.setUsadoEn(ahora);
        usuario.setHashContrasena(passwordEncoder.encode(request.nuevaContrasena()));
        usuario.setContrasenaCambiadaEn(ahora);

        // El flush también guarda usado_en del token (está en el mismo contexto de persistencia)
        Usuario guardado = usuarioRepository.saveAndFlush(usuario);
        tokenRepository.invalidarTodosActivos(guardado.getId(), ahora);
        eventos.contrasenaCambiada(guardado, motivoDe(vigente.getProposito()), ahora.toInstant());
    }

    private Usuario buscarUsuario(UsuarioAutenticado autenticado) {
        return usuarioRepository.findById(autenticado.id())
                .orElseThrow(() -> new UsuarioNoEncontradoException(autenticado.id()));
    }

    private static MotivoContrasena motivoDe(PropositoContrasena proposito) {
        return proposito == PropositoContrasena.ACTIVACION
                ? MotivoContrasena.ACTIVACION
                : MotivoContrasena.RESTABLECIMIENTO;
    }
}
