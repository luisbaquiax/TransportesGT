package org.transportsgt.identityservice.services.consulta.impl;

import org.transportsgt.identityservice.repositories.UsuarioSpecs;
import org.transportsgt.identityservice.repositories.SucursalSpecs;
import org.transportsgt.identityservice.mappers.SucursalMapper;
import org.transportsgt.identityservice.mappers.FabricaPaginas;
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
import org.transportsgt.eventos.payload.identidad.PropositoContrasena;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.identityservice.config.security.authentication.UsuarioAutenticado;
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
class ConsultaUsuariosTest {

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
    private ConsultaIdentidadServiceImpl usuarioService;
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
        usuarioService = new ConsultaIdentidadServiceImpl(usuarioRepository, sucursalRepository, new UsuarioSpecs(),
                new SucursalSpecs(), buscador, autorizacion, usuarioMapper, Mappers.getMapper(SucursalMapper.class),
                new FabricaPaginas());
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
    @SuppressWarnings("unchecked")
    void listar_adminSistemaVeTodos() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        when(usuarioRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(cajero, adminSucursal), pageable, 2));

        // Act
        PaginaResponse<UsuarioResponse> result = usuarioService.listarUsuarios(null, null, null, null, pageable,
                autenticadoAdminSistema);

        // Assert
        assertAll(
                () -> assertEquals(2, result.totalElementos()),
                () -> assertEquals(ID_CAJERO, result.contenido().get(0).id())
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void listar_adminSucursalVeSoloSuSucursal() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        when(usuarioRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(cajero), pageable, 1));

        // Act
        PaginaResponse<UsuarioResponse> result = usuarioService.listarUsuarios(Rol.CAJERO, ID_SUCURSAL, true, "ana",
                pageable, autenticadoAdminSucursal);

        // Assert
        assertEquals(1, result.contenido().size());
    }

    @Test
    void listar_cuandoAdminSucursalPideOtraSucursal_lanzaOperacionNoAutorizada() {
        assertThrows(OperacionNoAutorizadaException.class, () -> usuarioService.listarUsuarios(null, ID_OTRA_SUCURSAL,
                null, null, PageRequest.of(0, 20), autenticadoAdminSucursal));
    }

    @Test
    void listar_cuandoCajero_lanzaOperacionNoAutorizada() {
        assertThrows(OperacionNoAutorizadaException.class, () -> usuarioService.listarUsuarios(null, null,
                null, null, PageRequest.of(0, 20), UsuarioFixture.autenticado(cajero)));
    }

    @Test
    void obtener_adminSucursalVeUsuarioDeSuSucursal() {
        // Arrange
        when(usuarioRepository.findById(ID_CAJERO)).thenReturn(Optional.of(cajero));

        // Act
        UsuarioResponse result = usuarioService.obtenerUsuario(ID_CAJERO, autenticadoAdminSucursal);

        // Assert
        assertEquals(ID_CAJERO, result.id());
    }

    @Test
    void obtener_usuarioSeVeASiMismo() {
        // Arrange
        when(usuarioRepository.findById(ID_CAJERO)).thenReturn(Optional.of(cajero));

        // Act
        UsuarioResponse result = usuarioService.obtenerUsuario(ID_CAJERO, UsuarioFixture.autenticado(cajero));

        // Assert
        assertEquals(cajero.getCorreo(), result.correo());
    }

    @Test
    void obtener_adminSistemaVeACualquiera() {
        // Arrange
        when(usuarioRepository.findById(ID_CAJERO)).thenReturn(Optional.of(cajero));

        // Act
        UsuarioResponse result = usuarioService.obtenerUsuario(ID_CAJERO, autenticadoAdminSistema);

        // Assert
        assertEquals(ID_CAJERO, result.id());
    }

    @Test
    void obtener_cuandoCajeroConsultaAOtro_lanzaOperacionNoAutorizada() {
        // Arrange
        when(usuarioRepository.findById(ID_ADMIN_SUCURSAL)).thenReturn(Optional.of(adminSucursal));

        // Assert
        assertThrows(OperacionNoAutorizadaException.class,
                () -> usuarioService.obtenerUsuario(ID_ADMIN_SUCURSAL, UsuarioFixture.autenticado(cajero)));
    }

    @Test
    void obtener_cuandoNoExiste_lanzaNoEncontrado() {
        // Arrange
        when(usuarioRepository.findById(ID_NUEVO)).thenReturn(Optional.empty());

        // Assert
        assertThrows(UsuarioNoEncontradoException.class,
                () -> usuarioService.obtenerUsuario(ID_NUEVO, autenticadoAdminSistema));
    }
}
