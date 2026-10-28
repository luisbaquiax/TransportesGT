package org.transportsgt.identityservice.services.sesion.impl;

import org.transportsgt.identityservice.repositories.SucursalRepository;
import org.transportsgt.identityservice.utils.NormalizadorCorreo;
import org.transportsgt.identityservice.services.comun.BuscadorIdentidad;
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
import org.transportsgt.eventos.payload.identidad.MotivoContrasena;
import org.transportsgt.eventos.payload.identidad.PropositoContrasena;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.identityservice.config.security.jwt.JwtService;
import org.transportsgt.identityservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.identityservice.config.security.authentication.UsuarioPrincipal;
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
import org.transportsgt.identityservice.services.contrasena.EnlacesContrasenaService;

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
class SesionServiceImplTest {

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
    private EnlacesContrasenaService tokenContrasenaService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EventosIdentidad eventos;
    @Mock
    private SucursalRepository sucursalRepository;

    private final UsuarioMapper usuarioMapper = Mappers.getMapper(UsuarioMapper.class);
    private SesionServiceImpl authService;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        authService = new SesionServiceImpl(authenticationManager, jwtService,
                new BuscadorIdentidad(usuarioRepository, sucursalRepository), new NormalizadorCorreo(), usuarioMapper);
        usuario = UsuarioFixture.cajero(ID_USUARIO, SucursalFixture.activa(ID_SUCURSAL));
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

    @Test
    void login_devuelveTokenYDatosDelUsuario() {
        // Arrange
        UsuarioPrincipal principal = UsuarioFixture.principal(usuario);
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

    @Test
    void yo_devuelveElUsuarioAutenticado() {
        // Arrange
        when(usuarioRepository.findById(ID_USUARIO)).thenReturn(Optional.of(usuario));

        // Act
        UsuarioResponse result = authService.miPerfil(UsuarioFixture.autenticado(usuario));

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
        assertThrows(UsuarioNoEncontradoException.class, () -> authService.miPerfil(UsuarioFixture.autenticado(usuario)));
    }
}
