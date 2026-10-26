package org.transportsgt.identityservice.services.usuario.impl;

import org.transportsgt.identityservice.utils.NormalizadorCorreo;
import org.transportsgt.identityservice.services.comun.BuscadorIdentidad;
import org.transportsgt.identityservice.services.autorizacion.AutorizacionUsuarios;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.transportsgt.eventos.payload.PropositoContrasena;
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.identityservice.config.security.UsuarioAutenticado;
import org.transportsgt.identityservice.dto.request.ActualizarPerfilRequest;
import org.transportsgt.identityservice.dto.request.ActualizarUsuarioRequest;
import org.transportsgt.identityservice.dto.request.CrearUsuarioRequest;
import org.transportsgt.identityservice.dto.response.PaginaResponse;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;
import org.transportsgt.identityservice.eventos.EventosIdentidad;
import org.transportsgt.identityservice.exception.ActivacionNoPendienteException;
import org.transportsgt.identityservice.exception.CorreoDuplicadoException;
import org.transportsgt.identityservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.identityservice.exception.RegistroModificadoException;
import org.transportsgt.identityservice.exception.RolNoPermitidoException;
import org.transportsgt.identityservice.exception.SucursalInactivaException;
import org.transportsgt.identityservice.exception.SucursalNoEncontradaException;
import org.transportsgt.identityservice.exception.SucursalNoPermitidaException;
import org.transportsgt.identityservice.exception.UltimoAdministradorSistemaException;
import org.transportsgt.identityservice.exception.UltimoAdministradorSucursalException;
import org.transportsgt.identityservice.exception.UsuarioNoEncontradoException;
import org.transportsgt.identityservice.fixtures.SucursalFixture;
import org.transportsgt.identityservice.fixtures.UsuarioFixture;
import org.transportsgt.identityservice.mappers.UsuarioMapper;
import org.transportsgt.identityservice.models.Sucursal;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.SucursalRepository;
import org.transportsgt.identityservice.repositories.UsuarioRepository;
import org.transportsgt.identityservice.services.contrasena.EnlacesContrasenaService;
import org.transportsgt.identityservice.utils.GeneradorTokenSeguro;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AltaPersonalServiceImplTest {

    private static final UUID ID_ADMIN_SISTEMA = UUID.randomUUID();
    private static final UUID ID_ADMIN_SUCURSAL = UUID.randomUUID();
    private static final UUID ID_CAJERO = UUID.randomUUID();
    private static final UUID ID_NUEVO = UUID.randomUUID();
    private static final UUID ID_SUCURSAL = UUID.randomUUID();
    private static final UUID ID_OTRA_SUCURSAL = UUID.randomUUID();
    private static final String CORREO = "cajero@transportes.gt";
    private static final String NOMBRE = "Ana López";
    private static final String IP = "10.0.0.1";
    private static final String CONTRASENA_ALEATORIA = "aleatoria";
    private static final String HASH_ALEATORIO = "$2a$10$hashAleatorio";

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private SucursalRepository sucursalRepository;
    @Mock
    private EnlacesContrasenaService tokenContrasenaService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private GeneradorTokenSeguro generadorToken;
    @Mock
    private EventosIdentidad eventos;
    @Spy
    private UsuarioMapper usuarioMapper = Mappers.getMapper(UsuarioMapper.class);
    private AltaPersonalServiceImpl usuarioService;
    private NormalizadorCorreo correos;
    private BuscadorIdentidad buscador;
    private AutorizacionUsuarios autorizacion;

    private Sucursal sucursal;
    private Usuario adminSistema;
    private Usuario adminSucursal;
    private Usuario cajero;
    private UsuarioAutenticado autenticadoAdminSistema;
    private UsuarioAutenticado autenticadoAdminSucursal;

    @BeforeEach
    void setUp() {
        sucursal = SucursalFixture.activa(ID_SUCURSAL);
        adminSistema = UsuarioFixture.adminSistema(ID_ADMIN_SISTEMA);
        adminSucursal = UsuarioFixture.adminSucursal(ID_ADMIN_SUCURSAL, sucursal);
        cajero = UsuarioFixture.cajero(ID_CAJERO, sucursal);
        autenticadoAdminSistema = UsuarioFixture.autenticado(adminSistema);
        autenticadoAdminSucursal = UsuarioFixture.autenticado(adminSucursal);
        correos = new NormalizadorCorreo();
        buscador = new BuscadorIdentidad(usuarioRepository, sucursalRepository);
        autorizacion = new AutorizacionUsuarios();
        usuarioService = new AltaPersonalServiceImpl(usuarioRepository, new ValidadorUsuario(usuarioRepository),
                tokenContrasenaService, passwordEncoder, generadorToken, correos, eventos);
    }

    private void prepararGuardado() {
        when(generadorToken.generar()).thenReturn(CONTRASENA_ALEATORIA);
        when(passwordEncoder.encode(CONTRASENA_ALEATORIA)).thenReturn(HASH_ALEATORIO);
        when(usuarioRepository.saveAndFlush(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            u.setId(ID_NUEVO);
            u.setVersion(0L);
            return u;
        });
    }

    @Test
    void registrarPersonal_creaUsuarioConContrasenaAleatoriaYEmiteActivacion() {
        // Arrange
        prepararGuardado();
        ArgumentCaptor<Usuario> usuarioCaptor = ArgumentCaptor.forClass(Usuario.class);
        when(usuarioRepository.existsByCorreoIgnoreCase(CORREO)).thenReturn(false);
        when(usuarioRepository.findById(ID_ADMIN_SUCURSAL)).thenReturn(Optional.of(adminSucursal));

        // Act
        Usuario result = usuarioService.registrarPersonal(" " + NOMBRE + " ", "  Cajero@Transportes.GT ",
                Rol.CAJERO, sucursal, ID_ADMIN_SUCURSAL, IP);

        // Assert
        assertAll(
                () -> verify(usuarioRepository).saveAndFlush(usuarioCaptor.capture()),
                () -> assertEquals(CORREO, usuarioCaptor.getValue().getCorreo()),
                () -> assertEquals(NOMBRE, usuarioCaptor.getValue().getNombreCompleto()),
                () -> assertEquals(HASH_ALEATORIO, usuarioCaptor.getValue().getHashContrasena()),
                () -> assertEquals(ID_ADMIN_SUCURSAL, usuarioCaptor.getValue().getCreadoPor()),
                () -> assertNull(usuarioCaptor.getValue().getContrasenaCambiadaEn()),
                () -> verify(eventos).usuarioCreado(result),
                () -> verify(tokenContrasenaService).emitir(result, PropositoContrasena.ACTIVACION, IP),
                () -> assertEquals(ID_NUEVO, result.getId())
        );
    }

    @Test
    void registrarPersonal_cuandoRolCliente_lanzaRolNoPermitido() {
        assertThrows(RolNoPermitidoException.class, () -> usuarioService.registrarPersonal(NOMBRE, CORREO,
                Rol.CLIENTE, null, ID_ADMIN_SISTEMA, IP));
        verifyNoInteractions(usuarioRepository, eventos, tokenContrasenaService);
    }

    @Test
    void registrarPersonal_cuandoRolDeSucursalSinSucursal_lanzaSucursalNoPermitida() {
        assertThrows(SucursalNoPermitidaException.class, () -> usuarioService.registrarPersonal(NOMBRE, CORREO,
                Rol.CAJERO, null, ID_ADMIN_SUCURSAL, IP));
    }

    @Test
    void registrarPersonal_cuandoAdminSistemaConSucursal_lanzaSucursalNoPermitida() {
        assertThrows(SucursalNoPermitidaException.class, () -> usuarioService.registrarPersonal(NOMBRE, CORREO,
                Rol.ADMIN_SISTEMA, sucursal, ID_ADMIN_SISTEMA, IP));
    }

    @Test
    void registrarPersonal_cuandoSucursalInactiva_lanzaSucursalInactiva() {
        Sucursal inactiva = SucursalFixture.inactiva(ID_SUCURSAL);

        assertThrows(SucursalInactivaException.class, () -> usuarioService.registrarPersonal(NOMBRE, CORREO,
                Rol.CAJERO, inactiva, ID_ADMIN_SUCURSAL, IP));
    }

    @Test
    void registrarPersonal_cuandoCorreoDuplicado_lanzaConflicto() {
        // Arrange
        when(usuarioRepository.existsByCorreoIgnoreCase(CORREO)).thenReturn(true);

        // Assert
        assertThrows(CorreoDuplicadoException.class, () -> usuarioService.registrarPersonal(NOMBRE, CORREO,
                Rol.CAJERO, sucursal, ID_ADMIN_SUCURSAL, IP));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void registrarPersonal_cuandoCreadorInactivo_lanzaOperacionNoAutorizada() {
        // Arrange
        adminSucursal.setActivo(false);
        when(usuarioRepository.existsByCorreoIgnoreCase(CORREO)).thenReturn(false);
        when(usuarioRepository.findById(ID_ADMIN_SUCURSAL)).thenReturn(Optional.of(adminSucursal));

        // Assert
        assertThrows(OperacionNoAutorizadaException.class, () -> usuarioService.registrarPersonal(NOMBRE, CORREO,
                Rol.CAJERO, sucursal, ID_ADMIN_SUCURSAL, IP));
    }

    @Test
    void registrarPersonal_cuandoAdminSucursalLoCreaQuienNoEsAdminSistema_lanzaOperacionNoAutorizada() {
        // Arrange
        when(usuarioRepository.existsByCorreoIgnoreCase(CORREO)).thenReturn(false);
        when(usuarioRepository.findById(ID_ADMIN_SUCURSAL)).thenReturn(Optional.of(adminSucursal));

        // Assert
        assertThrows(OperacionNoAutorizadaException.class, () -> usuarioService.registrarPersonal(NOMBRE, CORREO,
                Rol.ADMIN_SUCURSAL, sucursal, ID_ADMIN_SUCURSAL, IP));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }
}
