package org.transportsgt.identityservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import org.transportsgt.identityservice.eventos.EventosIdentidad;
import org.transportsgt.identityservice.exception.ContrasenaActualIncorrectaException;
import org.transportsgt.identityservice.exception.ContrasenaRepetidaException;
import org.transportsgt.identityservice.exception.CorreoDuplicadoException;
import org.transportsgt.identityservice.exception.CredencialesInvalidasException;
import org.transportsgt.identityservice.exception.TokenContrasenaInvalidoException;
import org.transportsgt.identityservice.exception.UsuarioInactivoException;
import org.transportsgt.identityservice.exception.UsuarioNoEncontradoException;
import org.transportsgt.identityservice.fixtures.SucursalFixture;
import org.transportsgt.identityservice.fixtures.UsuarioFixture;
import org.transportsgt.identityservice.mappers.UsuarioMapper;
import org.transportsgt.identityservice.models.TokenContrasena;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.TokenContrasenaRepository;
import org.transportsgt.identityservice.repositories.UsuarioRepository;
import org.transportsgt.identityservice.services.TokenContrasenaService;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final Instant AHORA = Instant.parse("2026-10-07T16:00:00Z");
    private static final OffsetDateTime AHORA_ODT = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC);
    private static final UUID ID_USUARIO = UUID.randomUUID();
    private static final UUID ID_SUCURSAL = UUID.randomUUID();
    private static final String CORREO = "cliente@transportes.gt";
    private static final String CONTRASENA = "Cliente1234";
    private static final String NUEVA_CONTRASENA = "NuevaClave2026";
    private static final String HASH_NUEVO = "$2a$10$hashNuevo";
    private static final String TOKEN = "tokenEnClaro";
    private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.e30.firma";
    private static final String IP = "10.0.0.1";

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private TokenContrasenaRepository tokenRepository;
    @Mock
    private TokenContrasenaService tokenContrasenaService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EventosIdentidad eventos;

    private final UsuarioMapper usuarioMapper = Mappers.getMapper(UsuarioMapper.class);
    private AuthServiceImpl authService;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(authenticationManager, jwtService, usuarioRepository, tokenRepository,
                tokenContrasenaService, passwordEncoder, usuarioMapper, eventos, Clock.fixed(AHORA, ZoneOffset.UTC));
        usuario = UsuarioFixture.cajero(ID_USUARIO, SucursalFixture.activa(ID_SUCURSAL));
    }

    // ---------- login ----------

    @Test
    void login_devuelveTokenYDatosDelUsuario() {
        // Arrange
        UsuarioPrincipal principal = UsuarioPrincipal.from(usuario);
        Authentication autenticado = UsernamePasswordAuthenticationToken.authenticated(principal, null,
                principal.getAuthorities());
        ArgumentCaptor<Authentication> solicitud = ArgumentCaptor.forClass(Authentication.class);
        when(authenticationManager.authenticate(any())).thenReturn(autenticado);
        when(jwtService.generarToken(principal)).thenReturn(new JwtService.TokenEmitido(JWT, AHORA.plusSeconds(3600)));

        // Act
        LoginResponse result = authService.login(new LoginRequest(" Cajero@Transportes.GT ", CONTRASENA));

        // Assert
        assertAll(
                () -> verify(authenticationManager).authenticate(solicitud.capture()),
                () -> assertEquals("cajero@transportes.gt", solicitud.getValue().getName()),
                () -> assertEquals(JWT, result.token()),
                () -> assertEquals(AHORA.plusSeconds(3600), result.expiraEn()),
                () -> assertEquals(ID_USUARIO, result.usuario().id()),
                () -> assertEquals(Rol.CAJERO, result.usuario().rol()),
                () -> assertEquals(ID_SUCURSAL, result.usuario().idSucursal())
        );
    }

    @Test
    void login_cuandoCredencialesMalas_lanzaCredencialesInvalidas() {
        // Arrange
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("malas"));

        // Assert
        assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(new LoginRequest(CORREO, "Incorrecta1")));
        verifyNoInteractions(jwtService);
    }

    @Test
    void login_cuandoUsuarioInactivo_lanzaUsuarioInactivo() {
        // Arrange
        when(authenticationManager.authenticate(any())).thenThrow(new DisabledException("inactivo"));

        // Assert
        assertThrows(UsuarioInactivoException.class, () -> authService.login(new LoginRequest(CORREO, CONTRASENA)));
    }

    // ---------- registrar ----------

    @Test
    void registrar_creaClienteYPublicaUsuarioCreado() {
        // Arrange
        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        when(usuarioRepository.existsByCorreoIgnoreCase(CORREO)).thenReturn(false);
        when(passwordEncoder.encode(CONTRASENA)).thenReturn(HASH_NUEVO);
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(ID_USUARIO);
            u.setVersion(0L);
            return u;
        });

        // Act
        UsuarioResponse result = authService.registrar(new RegistroRequest(" Carlos Méndez ", "Cliente@Transportes.gt",
                CONTRASENA));

        // Assert
        assertAll(
                () -> verify(usuarioRepository).saveAndFlush(captor.capture()),
                () -> assertEquals(Rol.CLIENTE, captor.getValue().getRol()),
                () -> assertEquals(CORREO, captor.getValue().getCorreo()),
                () -> assertEquals(HASH_NUEVO, captor.getValue().getHashContrasena()),
                () -> assertNull(captor.getValue().getSucursal()),
                () -> assertNull(captor.getValue().getCreadoPor()),
                () -> assertEquals(AHORA_ODT, captor.getValue().getContrasenaCambiadaEn()),
                () -> verify(eventos).usuarioCreado(captor.getValue()),
                () -> assertEquals("Carlos Méndez", result.nombreCompleto())
        );
    }

    @Test
    void registrar_cuandoCorreoDuplicado_lanzaConflicto() {
        // Arrange
        when(usuarioRepository.existsByCorreoIgnoreCase(CORREO)).thenReturn(true);

        // Assert
        assertThrows(CorreoDuplicadoException.class,
                () -> authService.registrar(new RegistroRequest("Carlos", CORREO, CONTRASENA)));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    // ---------- yo ----------

    @Test
    void yo_devuelveElUsuarioAutenticado() {
        // Arrange
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(usuario));

        // Act
        UsuarioResponse result = authService.miPerfil(UsuarioAutenticado.de(usuario));

        // Assert
        assertAll(
                () -> assertEquals(ID_USUARIO, result.id()),
                () -> assertEquals(SucursalFixture.NOMBRE, result.nombreSucursal())
        );
    }

    @Test
    void yo_cuandoNoExiste_lanzaNoEncontrado() {
        // Arrange
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.empty());

        // Assert
        assertThrows(UsuarioNoEncontradoException.class, () -> authService.miPerfil(UsuarioAutenticado.de(usuario)));
    }

    // ---------- cambiarContrasena ----------

    @Test
    void cambiarContrasena_actualizaHashEInvalidaEnlacesYPublicaCambio() {
        // Arrange
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(CONTRASENA, UsuarioFixture.HASH)).thenReturn(true);
        when(passwordEncoder.matches(NUEVA_CONTRASENA, UsuarioFixture.HASH)).thenReturn(false);
        when(passwordEncoder.encode(NUEVA_CONTRASENA)).thenReturn(HASH_NUEVO);
        when(usuarioRepository.saveAndFlush(usuario)).thenReturn(usuario);

        // Act
        authService.cambiarContrasena(new CambiarContrasenaRequest(CONTRASENA, NUEVA_CONTRASENA),
                UsuarioAutenticado.de(usuario));

        // Assert
        assertAll(
                () -> assertEquals(HASH_NUEVO, usuario.getHashContrasena()),
                () -> assertEquals(AHORA_ODT, usuario.getContrasenaCambiadaEn()),
                () -> verify(tokenRepository).invalidarTodosActivos(ID_USUARIO, AHORA_ODT),
                () -> verify(eventos).contrasenaCambiada(usuario, MotivoContrasena.CAMBIO, AHORA)
        );
    }

    @Test
    void cambiarContrasena_cuandoActualIncorrecta_lanzaReglaNegocio() {
        // Arrange
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Mala12345", UsuarioFixture.HASH)).thenReturn(false);

        // Assert
        assertThrows(ContrasenaActualIncorrectaException.class, () -> authService.cambiarContrasena(
                new CambiarContrasenaRequest("Mala12345", NUEVA_CONTRASENA), UsuarioAutenticado.de(usuario)));
        verifyNoInteractions(eventos);
    }

    @Test
    void cambiarContrasena_cuandoNuevaIgualALaActual_lanzaContrasenaRepetida() {
        // Arrange
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(CONTRASENA, UsuarioFixture.HASH)).thenReturn(true);

        // Assert
        assertThrows(ContrasenaRepetidaException.class, () -> authService.cambiarContrasena(
                new CambiarContrasenaRequest(CONTRASENA, CONTRASENA), UsuarioAutenticado.de(usuario)));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void cambiarContrasena_cuandoUsuarioInactivo_lanzaUsuarioInactivo() {
        // Arrange
        usuario.setActivo(false);
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(usuario));

        // Assert
        assertThrows(UsuarioInactivoException.class, () -> authService.cambiarContrasena(
                new CambiarContrasenaRequest(CONTRASENA, NUEVA_CONTRASENA), UsuarioAutenticado.de(usuario)));
    }

    // ---------- olvideContrasena ----------

    @Test
    void olvideContrasena_emiteEnlaceDeRestablecimiento() {
        // Arrange
        when(usuarioRepository.findByCorreoIgnoreCase(CORREO)).thenReturn(Optional.of(usuario));
        when(tokenContrasenaService.superaLimiteRestablecimiento(ID_USUARIO)).thenReturn(false);

        // Act
        authService.olvideContrasena(new OlvideContrasenaRequest(" CLIENTE@transportes.gt"), IP);

        // Assert
        verify(tokenContrasenaService).emitir(usuario, PropositoContrasena.RESTABLECIMIENTO, IP);
    }

    @Test
    void olvideContrasena_cuandoCorreoNoExiste_noHaceNada() {
        // Arrange
        when(usuarioRepository.findByCorreoIgnoreCase(CORREO)).thenReturn(Optional.empty());

        // Act
        authService.olvideContrasena(new OlvideContrasenaRequest(CORREO), IP);

        // Assert
        verifyNoInteractions(tokenContrasenaService);
    }

    @Test
    void olvideContrasena_cuandoUsuarioInactivo_noEmite() {
        // Arrange
        usuario.setActivo(false);
        when(usuarioRepository.findByCorreoIgnoreCase(CORREO)).thenReturn(Optional.of(usuario));

        // Act
        authService.olvideContrasena(new OlvideContrasenaRequest(CORREO), IP);

        // Assert
        verifyNoInteractions(tokenContrasenaService);
    }

    @Test
    void olvideContrasena_cuandoSuperaLimite_noEmite() {
        // Arrange
        when(usuarioRepository.findByCorreoIgnoreCase(CORREO)).thenReturn(Optional.of(usuario));
        when(tokenContrasenaService.superaLimiteRestablecimiento(ID_USUARIO)).thenReturn(true);

        // Act
        authService.olvideContrasena(new OlvideContrasenaRequest(CORREO), IP);

        // Assert
        verify(tokenContrasenaService, never()).emitir(any(), any(), any());
    }

    // ---------- validarEnlace ----------

    @Test
    void validarEnlace_devuelvePropositoYCorreoEnmascarado() {
        // Arrange
        usuario.setCorreo("ana.lopez@transportes.gt");
        TokenContrasena token = TokenContrasena.builder().usuario(usuario)
                .proposito(PropositoContrasena.RESTABLECIMIENTO).build();
        when(tokenContrasenaService.buscarVigente(TOKEN)).thenReturn(token);

        // Act
        EnlaceContrasenaResponse result = authService.validarEnlace(TOKEN);

        // Assert
        assertAll(
                () -> assertEquals(PropositoContrasena.RESTABLECIMIENTO, result.proposito()),
                () -> assertEquals("a***z@transportes.gt", result.correoEnmascarado())
        );
    }

    @Test
    void validarEnlace_cuandoTokenInvalido_propagaGone() {
        // Arrange
        when(tokenContrasenaService.buscarVigente(TOKEN)).thenThrow(new TokenContrasenaInvalidoException());

        // Assert
        assertThrows(TokenContrasenaInvalidoException.class, () -> authService.validarEnlace(TOKEN));
    }

    // ---------- restablecerContrasena ----------

    @Test
    void restablecerContrasena_conRestablecimiento_marcaUsadoYPublicaMotivoRestablecimiento() {
        // Arrange
        TokenContrasena token = tokenDe(PropositoContrasena.RESTABLECIMIENTO);
        when(tokenContrasenaService.buscarVigente(TOKEN)).thenReturn(token);
        when(passwordEncoder.matches(NUEVA_CONTRASENA, UsuarioFixture.HASH)).thenReturn(false);
        when(passwordEncoder.encode(NUEVA_CONTRASENA)).thenReturn(HASH_NUEVO);
        when(usuarioRepository.saveAndFlush(usuario)).thenReturn(usuario);

        // Act
        authService.restablecerContrasena(new RestablecerContrasenaRequest(TOKEN, NUEVA_CONTRASENA));

        // Assert
        assertAll(
                () -> assertEquals(AHORA_ODT, token.getUsadoEn()),
                () -> assertEquals(HASH_NUEVO, usuario.getHashContrasena()),
                () -> assertEquals(AHORA_ODT, usuario.getContrasenaCambiadaEn()),
                () -> verify(tokenRepository).invalidarTodosActivos(ID_USUARIO, AHORA_ODT),
                () -> verify(eventos).contrasenaCambiada(usuario, MotivoContrasena.RESTABLECIMIENTO, AHORA)
        );
    }

    @Test
    void restablecerContrasena_conActivacion_publicaMotivoActivacion() {
        // Arrange
        usuario.setContrasenaCambiadaEn(null);
        TokenContrasena token = tokenDe(PropositoContrasena.ACTIVACION);
        when(tokenContrasenaService.buscarVigente(TOKEN)).thenReturn(token);
        when(passwordEncoder.matches(NUEVA_CONTRASENA, UsuarioFixture.HASH)).thenReturn(false);
        when(passwordEncoder.encode(NUEVA_CONTRASENA)).thenReturn(HASH_NUEVO);
        when(usuarioRepository.saveAndFlush(usuario)).thenReturn(usuario);

        // Act
        authService.restablecerContrasena(new RestablecerContrasenaRequest(TOKEN, NUEVA_CONTRASENA));

        // Assert
        assertAll(
                () -> assertEquals(AHORA_ODT, usuario.getContrasenaCambiadaEn()),
                () -> verify(eventos).contrasenaCambiada(eq(usuario), eq(MotivoContrasena.ACTIVACION), eq(AHORA))
        );
    }

    @Test
    void restablecerContrasena_cuandoNuevaIgualALaActual_lanzaContrasenaRepetida() {
        // Arrange
        when(tokenContrasenaService.buscarVigente(TOKEN)).thenReturn(tokenDe(PropositoContrasena.RESTABLECIMIENTO));
        when(passwordEncoder.matches(NUEVA_CONTRASENA, UsuarioFixture.HASH)).thenReturn(true);

        // Assert
        assertThrows(ContrasenaRepetidaException.class, () -> authService.restablecerContrasena(
                new RestablecerContrasenaRequest(TOKEN, NUEVA_CONTRASENA)));
        verifyNoInteractions(eventos, tokenRepository);
    }

    @Test
    void restablecerContrasena_cuandoTokenInvalido_lanzaGone() {
        // Arrange
        when(tokenContrasenaService.buscarVigente(TOKEN)).thenThrow(new TokenContrasenaInvalidoException());

        // Assert
        assertThrows(TokenContrasenaInvalidoException.class, () -> authService.restablecerContrasena(
                new RestablecerContrasenaRequest(TOKEN, NUEVA_CONTRASENA)));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    private TokenContrasena tokenDe(PropositoContrasena proposito) {
        return TokenContrasena.builder()
                .id(UUID.randomUUID())
                .usuario(usuario)
                .hashToken("a".repeat(64))
                .proposito(proposito)
                .expiraEn(AHORA_ODT.plusMinutes(30))
                .build();
    }
}
