package org.transportsgt.identityservice.services.usuario.impl;

import org.transportsgt.identityservice.utils.NormalizadorCorreo;
import org.transportsgt.identityservice.services.comun.BuscadorIdentidad;
import org.transportsgt.identityservice.services.autorizacion.AutorizacionUsuarios;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.transportsgt.eventos.payload.identidad.PropositoContrasena;
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.identityservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.identityservice.dto.request.ActualizarPerfilRequest;
import org.transportsgt.identityservice.dto.request.ActualizarUsuarioRequest;
import org.transportsgt.identityservice.dto.request.CrearUsuarioRequest;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;
import org.transportsgt.identityservice.eventos.EventosIdentidad;
import org.transportsgt.identityservice.exception.ActivacionNoPendienteException;
import org.transportsgt.identityservice.exception.CorreoDuplicadoException;
import org.transportsgt.identityservice.exception.OperacionNoAutorizadaException;
import org.transportsgt.identityservice.exception.RegistroModificadoException;
import org.transportsgt.identityservice.exception.RolNoPermitidoException;
import org.transportsgt.identityservice.exception.SucursalNoEncontradaException;
import org.transportsgt.identityservice.exception.UltimoAdministradorSistemaException;
import org.transportsgt.identityservice.exception.UltimoAdministradorSucursalException;
import org.transportsgt.identityservice.fixtures.SucursalFixture;
import org.transportsgt.identityservice.fixtures.UsuarioFixture;
import org.transportsgt.identityservice.mappers.UsuarioMapper;
import org.transportsgt.identityservice.models.Sucursal;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.SucursalRepository;
import org.transportsgt.identityservice.repositories.UsuarioRepository;
import org.transportsgt.identityservice.services.contrasena.EnlacesContrasenaService;
import org.transportsgt.identityservice.utils.GeneradorTokenSeguro;

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
class GestionUsuariosServiceImplTest {

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
    private GestionUsuariosServiceImpl usuarioService;
    private NormalizadorCorreo correos;
    private BuscadorIdentidad buscador;
    private AutorizacionUsuarios autorizacion;
    private ReglasAdministradores reglas;

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
        ValidadorUsuario validador = new ValidadorUsuario(usuarioRepository);
        reglas = new ReglasAdministradores(usuarioRepository);
        RegistroPersonalServiceImpl altaPersonal = new RegistroPersonalServiceImpl(usuarioRepository, validador,
                tokenContrasenaService, passwordEncoder, generadorToken, correos, eventos);
        usuarioService = new GestionUsuariosServiceImpl(usuarioRepository, altaPersonal, tokenContrasenaService,
                autorizacion, validador, reglas, buscador, correos, usuarioMapper, eventos);
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
                () -> usuarioService.crear(request, UsuarioFixture.autenticado(cajero), IP));
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
        reglas.verificarNoEsUltimo(adminSistema);

        // Assert
        verifyNoInteractions(usuarioRepository);
    }

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

    @Test
    void actualizarPerfil_cambiaNombreDelAutenticadoYPublicaEvento() {
        // Arrange
        when(usuarioRepository.findById(ID_CAJERO)).thenReturn(Optional.of(cajero));
        when(usuarioRepository.saveAndFlush(cajero)).thenReturn(cajero);

        // Act
        UsuarioResponse result = usuarioService.actualizarPerfil(new ActualizarPerfilRequest(" Ana Nueva "),
                UsuarioFixture.autenticado(cajero));

        // Assert
        assertAll(
                () -> assertEquals("Ana Nueva", result.nombreCompleto()),
                () -> verify(eventos).usuarioActualizado(cajero)
        );
    }
}
