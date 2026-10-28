package org.transportsgt.identityservice.services.consulta.impl;

import org.transportsgt.identityservice.repositories.UsuarioSpecs;
import org.transportsgt.identityservice.repositories.SucursalSpecs;
import org.transportsgt.identityservice.mappers.FabricaPaginas;
import org.transportsgt.identityservice.services.autorizacion.AutorizacionUsuarios;
import org.transportsgt.identityservice.services.comun.BuscadorIdentidad;
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
import org.transportsgt.eventos.payload.identidad.Rol;
import org.transportsgt.identityservice.config.security.authentication.UsuarioAutenticado;
import org.transportsgt.identityservice.dto.request.ActualizarSucursalRequest;
import org.transportsgt.identityservice.dto.request.CrearSucursalRequest;
import org.transportsgt.identityservice.dto.request.NuevoAdministradorRequest;
import org.transportsgt.identityservice.dto.request.SucursalRequest;
import org.transportsgt.identityservice.dto.response.PaginaResponse;
import org.transportsgt.identityservice.dto.response.SucursalCreadaResponse;
import org.transportsgt.identityservice.dto.response.SucursalResponse;
import org.transportsgt.identityservice.dto.response.UsuarioResponse;
import org.transportsgt.identityservice.eventos.EventosIdentidad;
import org.transportsgt.identityservice.exception.NombreSucursalDuplicadoException;
import org.transportsgt.identityservice.exception.RegistroModificadoException;
import org.transportsgt.identityservice.exception.SucursalNoEncontradaException;
import org.transportsgt.identityservice.fixtures.SucursalFixture;
import org.transportsgt.identityservice.fixtures.UsuarioFixture;
import org.transportsgt.identityservice.mappers.SucursalMapper;
import org.transportsgt.identityservice.mappers.UsuarioMapper;
import org.transportsgt.identityservice.models.Sucursal;
import org.transportsgt.identityservice.models.Usuario;
import org.transportsgt.identityservice.repositories.SucursalRepository;
import org.transportsgt.identityservice.repositories.UsuarioRepository;
import org.transportsgt.identityservice.services.usuario.AltaPersonalService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultaSucursalesTest {

    private static final UUID ID_SUCURSAL = UUID.randomUUID();
    private static final UUID ID_ADMIN_SISTEMA = UUID.randomUUID();
    private static final UUID ID_ADMIN_SUCURSAL = UUID.randomUUID();
    private static final String NOMBRE = "Quetzaltenango Centro";
    private static final String NOMBRE_ACTUALIZADO = "Xela Terminal Minerva";
    private static final String CORREO_ADMIN = "admin.xela@transportes.gt";
    private static final String NOMBRE_ADMIN = "María Fernanda Pérez";
    private static final String IP = "10.0.0.1";

    @Mock
    private SucursalRepository sucursalRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AltaPersonalService usuarioService;
    @Mock
    private EventosIdentidad eventos;
    @Spy
    private SucursalMapper sucursalMapper = Mappers.getMapper(SucursalMapper.class);
    @Spy
    private UsuarioMapper usuarioMapper = Mappers.getMapper(UsuarioMapper.class);
    private ConsultaIdentidadServiceImpl sucursalService;
    private BuscadorIdentidad buscador;

    private UsuarioAutenticado adminSistema;
    private SucursalRequest datosSucursal;

    @BeforeEach
    void setUp() {
        adminSistema = UsuarioFixture.autenticado(UsuarioFixture.adminSistema(ID_ADMIN_SISTEMA));
        datosSucursal = new SucursalRequest("  " + NOMBRE + " ", "Quetzaltenango", "4a. Calle 12-35, Zona 1",
                "77651234", new BigDecimal("14.834500"), new BigDecimal("-91.518600"));

        buscador = new BuscadorIdentidad(usuarioRepository, sucursalRepository);
        sucursalService = new ConsultaIdentidadServiceImpl(usuarioRepository, sucursalRepository, new UsuarioSpecs(),
                new SucursalSpecs(), buscador, new AutorizacionUsuarios(), usuarioMapper, sucursalMapper,
                new FabricaPaginas());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listar_devuelvePaginaConvertida() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 20);
        Sucursal sucursal = SucursalFixture.activa(ID_SUCURSAL);
        when(sucursalRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(sucursal), pageable, 1));

        // Act
        PaginaResponse<SucursalResponse> result = sucursalService.listarSucursales(true, "xela", pageable);

        // Assert
        assertAll(
                () -> assertEquals(1, result.totalElementos()),
                () -> assertEquals(1, result.contenido().size()),
                () -> assertEquals(ID_SUCURSAL, result.contenido().get(0).id()),
                () -> assertEquals(0, result.pagina())
        );
    }

    @Test
    void obtener_devuelveSucursal() {
        // Arrange
        when(sucursalRepository.findById(ID_SUCURSAL)).thenReturn(Optional.of(SucursalFixture.activa(ID_SUCURSAL)));

        // Act
        SucursalResponse result = sucursalService.obtenerSucursal(ID_SUCURSAL);

        // Assert
        assertAll(
                () -> assertEquals(ID_SUCURSAL, result.id()),
                () -> assertEquals(SucursalFixture.NOMBRE, result.nombre())
        );
    }

    @Test
    void obtener_cuandoNoExiste_lanzaNoEncontrada() {
        // Arrange
        when(sucursalRepository.findById(ID_SUCURSAL)).thenReturn(Optional.empty());

        // Assert
        assertThrows(SucursalNoEncontradaException.class, () -> sucursalService.obtenerSucursal(ID_SUCURSAL));
    }

    @Test
    void listarAdministradores_devuelveAdministradoresDeLaSucursal() {
        // Arrange
        Sucursal sucursal = SucursalFixture.activa(ID_SUCURSAL);
        Usuario admin = UsuarioFixture.adminSucursal(ID_ADMIN_SUCURSAL, sucursal);
        when(sucursalRepository.findById(ID_SUCURSAL)).thenReturn(Optional.of(sucursal));
        when(usuarioRepository.findAllBySucursalIdAndRolOrderByNombreCompleto(ID_SUCURSAL, Rol.ADMIN_SUCURSAL))
                .thenReturn(List.of(admin));

        // Act
        List<UsuarioResponse> result = sucursalService.listarAdministradores(ID_SUCURSAL);

        // Assert
        assertAll(
                () -> assertEquals(1, result.size()),
                () -> assertEquals(ID_ADMIN_SUCURSAL, result.get(0).id()),
                () -> assertEquals(SucursalFixture.NOMBRE, result.get(0).nombreSucursal())
        );
    }

    @Test
    void listarAdministradores_cuandoSucursalNoExiste_lanzaNoEncontrada() {
        // Arrange
        when(sucursalRepository.findById(ID_SUCURSAL)).thenReturn(Optional.empty());

        // Assert
        assertThrows(SucursalNoEncontradaException.class, () -> sucursalService.listarAdministradores(ID_SUCURSAL));
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    void buscar_devuelveLaEntidad() {
        // Arrange
        Sucursal sucursal = SucursalFixture.activa(ID_SUCURSAL);
        when(sucursalRepository.findById(ID_SUCURSAL)).thenReturn(Optional.of(sucursal));

        // Act
        Sucursal result = buscador.sucursal(ID_SUCURSAL);

        // Assert
        assertSame(sucursal, result);
    }
}
