package org.transportsgt.identityservice.services.registro.impl;

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
class RegistroClienteServiceImplTest {

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
    private RegistroClienteServiceImpl authService;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        authService = new RegistroClienteServiceImpl(usuarioRepository, passwordEncoder, new NormalizadorCorreo(),
                usuarioMapper, eventos, Clock.fixed(AHORA, ZoneOffset.UTC));
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
}
