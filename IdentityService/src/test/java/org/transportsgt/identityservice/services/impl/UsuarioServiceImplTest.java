package org.transportsgt.identityservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
import org.transportsgt.identityservice.services.TokenContrasenaService;
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
class UsuarioServiceImplTest {

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
    private TokenContrasenaService tokenContrasenaService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private GeneradorTokenSeguro generadorToken;
    @Mock
    private EventosIdentidad eventos;
    @Spy
    private UsuarioMapper usuarioMapper = Mappers.getMapper(UsuarioMapper.class);
    @InjectMocks
    private UsuarioServiceImpl usuarioService;

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
        autenticadoAdminSistema = UsuarioAutenticado.de(adminSistema);
        autenticadoAdminSucursal = UsuarioAutenticado.de(adminSucursal);
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

    // ---------- registrarPersonal ----------

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

    // ---------- crear ----------

    @Test
    void crear_adminSistemaCreaAdminSucursal() {
        // Arrange
        prepararGuardado();
        CrearUsuarioRequest request = new CrearUsuarioRequest(NOMBRE, CORREO, Rol.ADMIN_SUCURSAL, ID_SUCURSAL);
        when(sucursalRepository.findById(ID_SUCURSAL)).thenReturn(Optional.of(sucursal));
        when(usuarioRepository.existsByCorreoIgnoreCase(CORREO)).thenReturn(false);
        when(usuarioRepository.findById(ID_ADMIN_SISTEMA)).thenReturn(Optional.of(adminSistema));

        // Act
        UsuarioResponse result = usuarioService.crear(request, autenticadoAdminSistema, IP);

        // Assert
        assertAll(
                () -> assertEquals(ID_NUEVO, result.id()),
                () -> assertEquals(Rol.ADMIN_SUCURSAL, result.rol()),
                () -> assertEquals(ID_SUCURSAL, result.idSucursal()),
                () -> assertTrue(result.activacionPendiente())
        );
    }

    @Test
    void crear_adminSistemaCreaAdminSistemaSinSucursal() {
        // Arrange
        prepararGuardado();
        CrearUsuarioRequest request = new CrearUsuarioRequest(NOMBRE, CORREO, Rol.ADMIN_SISTEMA, null);
        when(usuarioRepository.existsByCorreoIgnoreCase(CORREO)).thenReturn(false);
        when(usuarioRepository.findById(ID_ADMIN_SISTEMA)).thenReturn(Optional.of(adminSistema));

        // Act
        UsuarioResponse result = usuarioService.crear(request, autenticadoAdminSistema, IP);

        // Assert
        assertAll(
                () -> assertEquals(Rol.ADMIN_SISTEMA, result.rol()),
                () -> assertNull(result.idSucursal()),
                () -> verifyNoInteractions(sucursalRepository)
        );
    }

    @Test
    void crear_adminSucursalCreaCajeroEnSuSucursalAunqueOmitaIdSucursal() {
        // Arrange
        prepararGuardado();
        CrearUsuarioRequest request = new CrearUsuarioRequest(NOMBRE, CORREO, Rol.CAJERO, null);
        when(sucursalRepository.findById(ID_SUCURSAL)).thenReturn(Optional.of(sucursal));
        when(usuarioRepository.existsByCorreoIgnoreCase(CORREO)).thenReturn(false);
        when(usuarioRepository.findById(ID_ADMIN_SUCURSAL)).thenReturn(Optional.of(adminSucursal));

        // Act
        UsuarioResponse result = usuarioService.crear(request, autenticadoAdminSucursal, IP);

        // Assert
        assertAll(
                () -> assertEquals(Rol.CAJERO, result.rol()),
                () -> assertEquals(ID_SUCURSAL, result.idSucursal())
        );
    }

    @Test
    void crear_cuandoAdminSucursalUsaOtraSucursal_lanzaOperacionNoAutorizada() {
        CrearUsuarioRequest request = new CrearUsuarioRequest(NOMBRE, CORREO, Rol.CHOFER, ID_OTRA_SUCURSAL);

        assertThrows(OperacionNoAutorizadaException.class,
                () -> usuarioService.crear(request, autenticadoAdminSucursal, IP));
        verifyNoInteractions(sucursalRepository, usuarioRepository);
    }

    @Test
    void crear_cuandoAdminSucursalCreaAdminSucursal_lanzaOperacionNoAutorizada() {
        CrearUsuarioRequest request = new CrearUsuarioRequest(NOMBRE, CORREO, Rol.ADMIN_SUCURSAL, ID_SUCURSAL);

        assertThrows(OperacionNoAutorizadaException.class,
                () -> usuarioService.crear(request, autenticadoAdminSucursal, IP));
    }

    @Test
    void crear_cuandoAdminSistemaCreaCajero_lanzaOperacionNoAutorizada() {
        CrearUsuarioRequest request = new CrearUsuarioRequest(NOMBRE, CORREO, Rol.CAJERO, ID_SUCURSAL);

        assertThrows(OperacionNoAutorizadaException.class,
                () -> usuarioService.crear(request, autenticadoAdminSistema, IP));
    }

    @Test
    void crear_cuandoCajeroIntentaCrear_lanzaOperacionNoAutorizada() {
        CrearUsuarioRequest request = new CrearUsuarioRequest(NOMBRE, CORREO, Rol.CHOFER, ID_SUCURSAL);

        assertThrows(OperacionNoAutorizadaException.class,
                () -> usuarioService.crear(request, UsuarioAutenticado.de(cajero), IP));
    }

    @Test
    void crear_cuandoSucursalNoExiste_lanzaNoEncontrada() {
        // Arrange
        CrearUsuarioRequest request = new CrearUsuarioRequest(NOMBRE, CORREO, Rol.ADMIN_SUCURSAL, ID_OTRA_SUCURSAL);
        when(sucursalRepository.findById(ID_OTRA_SUCURSAL)).thenReturn(Optional.empty());

        // Assert
        assertThrows(SucursalNoEncontradaException.class,
                () -> usuarioService.crear(request, autenticadoAdminSistema, IP));
    }

    // ---------- listar ----------

    @Test
    @SuppressWarnings("unchecked")
    void listar_adminSistemaVeTodos() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        when(usuarioRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(cajero, adminSucursal), pageable, 2));

        // Act
        PaginaResponse<UsuarioResponse> result = usuarioService.listar(null, null, null, null, pageable,
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
        PaginaResponse<UsuarioResponse> result = usuarioService.listar(Rol.CAJERO, ID_SUCURSAL, true, "ana",
                pageable, autenticadoAdminSucursal);

        // Assert
        assertEquals(1, result.contenido().size());
    }

    @Test
    void listar_cuandoAdminSucursalPideOtraSucursal_lanzaOperacionNoAutorizada() {
        assertThrows(OperacionNoAutorizadaException.class, () -> usuarioService.listar(null, ID_OTRA_SUCURSAL,
                null, null, PageRequest.of(0, 20), autenticadoAdminSucursal));
    }

    @Test
    void listar_cuandoCajero_lanzaOperacionNoAutorizada() {
        assertThrows(OperacionNoAutorizadaException.class, () -> usuarioService.listar(null, null,
                null, null, PageRequest.of(0, 20), UsuarioAutenticado.de(cajero)));
    }

    // ---------- obtener ----------

    @Test
    void obtener_adminSucursalVeUsuarioDeSuSucursal() {
        // Arrange
        when(usuarioRepository.findById(ID_CAJERO)).thenReturn(Optional.of(cajero));

        // Act
        UsuarioResponse result = usuarioService.obtener(ID_CAJERO, autenticadoAdminSucursal);

        // Assert
        assertEquals(ID_CAJERO, result.id());
    }

    @Test
    void obtener_usuarioSeVeASiMismo() {
        // Arrange
        when(usuarioRepository.findById(ID_CAJERO)).thenReturn(Optional.of(cajero));

        // Act
        UsuarioResponse result = usuarioService.obtener(ID_CAJERO, UsuarioAutenticado.de(cajero));

        // Assert
        assertEquals(cajero.getCorreo(), result.correo());
    }

    @Test
    void obtener_adminSistemaVeACualquiera() {
        // Arrange
        when(usuarioRepository.findById(ID_CAJERO)).thenReturn(Optional.of(cajero));

        // Act
        UsuarioResponse result = usuarioService.obtener(ID_CAJERO, autenticadoAdminSistema);

        // Assert
        assertEquals(ID_CAJERO, result.id());
    }

    @Test
    void obtener_cuandoCajeroConsultaAOtro_lanzaOperacionNoAutorizada() {
        // Arrange
        when(usuarioRepository.findById(ID_ADMIN_SUCURSAL)).thenReturn(Optional.of(adminSucursal));

        // Assert
        assertThrows(OperacionNoAutorizadaException.class,
                () -> usuarioService.obtener(ID_ADMIN_SUCURSAL, UsuarioAutenticado.de(cajero)));
    }

    @Test
    void obtener_cuandoNoExiste_lanzaNoEncontrado() {
        // Arrange
        when(usuarioRepository.findById(ID_NUEVO)).thenReturn(Optional.empty());

        // Assert
        assertThrows(UsuarioNoEncontradoException.class,
                () -> usuarioService.obtener(ID_NUEVO, autenticadoAdminSistema));
    }

    // ---------- actualizar ----------

    @Test
    void actualizar_cambiaNombreYCorreoYPublicaUsuarioActualizado() {
        // Arrange
        ActualizarUsuarioRequest request = new ActualizarUsuarioRequest("Ana María López", " Nueva@Transportes.gt", 0L);
        when(usuarioRepository.findById(ID_CAJERO)).thenReturn(Optional.of(cajero));
        when(usuarioRepository.existsByCorreoIgnoreCaseAndIdNot("nueva@transportes.gt", ID_CAJERO)).thenReturn(false);
        when(usuarioRepository.saveAndFlush(cajero)).thenAnswer(inv -> {
            cajero.setVersion(1L);
            return cajero;
        });

        // Act
        UsuarioResponse result = usuarioService.actualizar(ID_CAJERO, request, autenticadoAdminSucursal);

        // Assert
        assertAll(
                () -> assertEquals("Ana María López", result.nombreCompleto()),
                () -> assertEquals("nueva@transportes.gt", result.correo()),
                () -> assertEquals(1L, result.version()),
                () -> verify(eventos).usuarioActualizado(cajero)
        );
    }

    @Test
    void actualizar_cuandoCorreoNoCambia_noConsultaDuplicados() {
        // Arrange
        ActualizarUsuarioRequest request = new ActualizarUsuarioRequest("Otro Nombre", cajero.getCorreo(), null);
        when(usuarioRepository.findById(ID_CAJERO)).thenReturn(Optional.of(cajero));
        when(usuarioRepository.saveAndFlush(cajero)).thenReturn(cajero);

        // Act
        usuarioService.actualizar(ID_CAJERO, request, autenticadoAdminSistema);

        // Assert
        verify(usuarioRepository, never()).existsByCorreoIgnoreCaseAndIdNot(any(), any());
    }

    @Test
    void actualizar_cuandoCorreoDuplicado_lanzaConflicto() {
        // Arrange
        ActualizarUsuarioRequest request = new ActualizarUsuarioRequest(NOMBRE, "otro@transportes.gt", null);
        when(usuarioRepository.findById(ID_CAJERO)).thenReturn(Optional.of(cajero));
        when(usuarioRepository.existsByCorreoIgnoreCaseAndIdNot("otro@transportes.gt", ID_CAJERO)).thenReturn(true);

        // Assert
        assertThrows(CorreoDuplicadoException.class,
                () -> usuarioService.actualizar(ID_CAJERO, request, autenticadoAdminSistema));
        verifyNoInteractions(eventos);
    }

    @Test
    void actualizar_cuandoVersionDesactualizada_lanzaConflicto() {
        // Arrange
        ActualizarUsuarioRequest request = new ActualizarUsuarioRequest(NOMBRE, CORREO, 3L);
        when(usuarioRepository.findById(ID_CAJERO)).thenReturn(Optional.of(cajero));

        // Assert
        assertThrows(RegistroModificadoException.class,
                () -> usuarioService.actualizar(ID_CAJERO, request, autenticadoAdminSistema));
    }

    @Test
    void actualizar_cuandoAdminSucursalEditaAdminSucursal_lanzaOperacionNoAutorizada() {
        // Arrange
        Usuario otroAdmin = UsuarioFixture.adminSucursal(UUID.randomUUID(), sucursal);
        ActualizarUsuarioRequest request = new ActualizarUsuarioRequest(NOMBRE, CORREO, null);
        when(usuarioRepository.findById(otroAdmin.getId())).thenReturn(Optional.of(otroAdmin));

        // Assert
        assertThrows(OperacionNoAutorizadaException.class,
                () -> usuarioService.actualizar(otroAdmin.getId(), request, autenticadoAdminSucursal));
    }

    @Test
    void actualizar_cuandoAdminSucursalEditaCajeroDeOtraSucursal_lanzaOperacionNoAutorizada() {
        // Arrange
        Usuario cajeroAjeno = UsuarioFixture.cajero(UUID.randomUUID(), SucursalFixture.activa(ID_OTRA_SUCURSAL));
        ActualizarUsuarioRequest request = new ActualizarUsuarioRequest(NOMBRE, CORREO, null);
        when(usuarioRepository.findById(cajeroAjeno.getId())).thenReturn(Optional.of(cajeroAjeno));

        // Assert
        assertThrows(OperacionNoAutorizadaException.class,
                () -> usuarioService.actualizar(cajeroAjeno.getId(), request, autenticadoAdminSucursal));
    }

    // ---------- cambiarEstado ----------

    @Test
    void cambiarEstado_desactivaCajeroYPublicaEvento() {
        // Arrange
        when(usuarioRepository.findById(ID_CAJERO)).thenReturn(Optional.of(cajero));
        when(usuarioRepository.saveAndFlush(cajero)).thenReturn(cajero);

        // Act
        UsuarioResponse result = usuarioService.cambiarEstado(ID_CAJERO, false, autenticadoAdminSucursal);

        // Assert
        assertAll(
                () -> assertFalse(result.activo()),
                () -> verify(eventos).usuarioActualizado(cajero),
                () -> verify(usuarioRepository, never()).countByRolAndActivoTrue(any())
        );
    }

    @Test
    void cambiarEstado_activaUsuarioInactivo() {
        // Arrange
        cajero.setActivo(false);
        when(usuarioRepository.findById(ID_CAJERO)).thenReturn(Optional.of(cajero));
        when(usuarioRepository.saveAndFlush(cajero)).thenReturn(cajero);

        // Act
        UsuarioResponse result = usuarioService.cambiarEstado(ID_CAJERO, true, autenticadoAdminSistema);

        // Assert
        assertTrue(result.activo());
    }

    @Test
    void cambiarEstado_cuandoYaTieneEseEstado_noPublicaEvento() {
        // Arrange
        when(usuarioRepository.findById(ID_CAJERO)).thenReturn(Optional.of(cajero));

        // Act
        usuarioService.cambiarEstado(ID_CAJERO, true, autenticadoAdminSistema);

        // Assert
        assertAll(
                () -> verify(usuarioRepository, never()).saveAndFlush(any()),
                () -> verifyNoInteractions(eventos)
        );
    }

    @Test
    void cambiarEstado_desactivaAdminSistemaCuandoHayOtro() {
        // Arrange
        when(usuarioRepository.findById(ID_ADMIN_SISTEMA)).thenReturn(Optional.of(adminSistema));
        when(usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN_SISTEMA)).thenReturn(2L);
        when(usuarioRepository.saveAndFlush(adminSistema)).thenReturn(adminSistema);

        // Act
        UsuarioResponse result = usuarioService.cambiarEstado(ID_ADMIN_SISTEMA, false, autenticadoAdminSistema);

        // Assert
        assertFalse(result.activo());
    }

    @Test
    void cambiarEstado_cuandoUltimoAdminSistema_lanzaReglaNegocio() {
        // Arrange
        when(usuarioRepository.findById(ID_ADMIN_SISTEMA)).thenReturn(Optional.of(adminSistema));
        when(usuarioRepository.countByRolAndActivoTrue(Rol.ADMIN_SISTEMA)).thenReturn(1L);

        // Assert
        assertThrows(UltimoAdministradorSistemaException.class,
                () -> usuarioService.cambiarEstado(ID_ADMIN_SISTEMA, false, autenticadoAdminSistema));
        verify(usuarioRepository, never()).saveAndFlush(any());
    }

    @Test
    void cambiarEstado_cuandoUltimoAdminSucursal_lanzaReglaNegocio() {
        // Arrange
        when(usuarioRepository.findById(ID_ADMIN_SUCURSAL)).thenReturn(Optional.of(adminSucursal));
        when(usuarioRepository.countBySucursalIdAndRolAndActivoTrue(ID_SUCURSAL, Rol.ADMIN_SUCURSAL)).thenReturn(1L);

        // Assert
        assertThrows(UltimoAdministradorSucursalException.class,
                () -> usuarioService.cambiarEstado(ID_ADMIN_SUCURSAL, false, autenticadoAdminSistema));
        verifyNoInteractions(eventos);
    }

    @Test
    void cambiarEstado_desactivaAdminSucursalCuandoHayOtroEnLaSucursal() {
        // Arrange
        when(usuarioRepository.findById(ID_ADMIN_SUCURSAL)).thenReturn(Optional.of(adminSucursal));
        when(usuarioRepository.countBySucursalIdAndRolAndActivoTrue(ID_SUCURSAL, Rol.ADMIN_SUCURSAL)).thenReturn(2L);
        when(usuarioRepository.saveAndFlush(adminSucursal)).thenReturn(adminSucursal);

        // Act
        UsuarioResponse result = usuarioService.cambiarEstado(ID_ADMIN_SUCURSAL, false, autenticadoAdminSistema);

        // Assert
        assertAll(
                () -> assertFalse(result.activo()),
                () -> verify(eventos).usuarioActualizado(adminSucursal)
        );
    }

    @Test
    void validarNoEsUltimoAdministrador_ignoraUsuariosInactivos() {
        // Arrange
        adminSistema.setActivo(false);

        // Act
        usuarioService.validarNoEsUltimoAdministrador(adminSistema);

        // Assert
        verifyNoInteractions(usuarioRepository);
    }

    // ---------- reenviarActivacion ----------

    @Test
    void reenviarActivacion_emiteNuevoEnlace() {
        // Arrange
        cajero.setContrasenaCambiadaEn(null);
        when(usuarioRepository.findById(ID_CAJERO)).thenReturn(Optional.of(cajero));

        // Act
        usuarioService.reenviarActivacion(ID_CAJERO, autenticadoAdminSucursal, IP);

        // Assert
        verify(tokenContrasenaService).emitir(cajero, PropositoContrasena.ACTIVACION, IP);
    }

    @Test
    void reenviarActivacion_cuandoYaActivo_lanzaActivacionNoPendiente() {
        // Arrange
        when(usuarioRepository.findById(ID_CAJERO)).thenReturn(Optional.of(cajero));

        // Assert
        assertThrows(ActivacionNoPendienteException.class,
                () -> usuarioService.reenviarActivacion(ID_CAJERO, autenticadoAdminSistema, IP));
        verifyNoInteractions(tokenContrasenaService);
    }

    @Test
    void reenviarActivacion_cuandoUsuarioInactivo_lanzaActivacionNoPendiente() {
        // Arrange
        cajero.setContrasenaCambiadaEn(null);
        cajero.setActivo(false);
        when(usuarioRepository.findById(ID_CAJERO)).thenReturn(Optional.of(cajero));

        // Assert
        assertThrows(ActivacionNoPendienteException.class,
                () -> usuarioService.reenviarActivacion(ID_CAJERO, autenticadoAdminSistema, IP));
    }

    @Test
    void reenviarActivacion_cuandoCliente_lanzaRolNoPermitido() {
        // Arrange
        Usuario cliente = UsuarioFixture.cliente(UUID.randomUUID());
        when(usuarioRepository.findById(cliente.getId())).thenReturn(Optional.of(cliente));

        // Assert
        assertThrows(RolNoPermitidoException.class,
                () -> usuarioService.reenviarActivacion(cliente.getId(), autenticadoAdminSistema, IP));
    }

    // ---------- actualizarPerfil ----------

    @Test
    void actualizarPerfil_cambiaNombreDelAutenticadoYPublicaEvento() {
        // Arrange
        when(usuarioRepository.findById(ID_CAJERO)).thenReturn(Optional.of(cajero));
        when(usuarioRepository.saveAndFlush(cajero)).thenReturn(cajero);

        // Act
        UsuarioResponse result = usuarioService.actualizarPerfil(new ActualizarPerfilRequest(" Ana Nueva "),
                UsuarioAutenticado.de(cajero));

        // Assert
        assertAll(
                () -> assertEquals("Ana Nueva", result.nombreCompleto()),
                () -> verify(eventos).usuarioActualizado(cajero)
        );
    }
}
