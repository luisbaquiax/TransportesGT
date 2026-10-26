package org.transportsgt.identityservice.services.sucursal.impl;

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
import org.transportsgt.eventos.payload.Rol;
import org.transportsgt.identityservice.config.security.UsuarioAutenticado;
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
class GestionSucursalesServiceImplTest {

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
    private GestionSucursalesServiceImpl sucursalService;
    private BuscadorIdentidad buscador;

    private UsuarioAutenticado adminSistema;
    private SucursalRequest datosSucursal;

    @BeforeEach
    void setUp() {
        adminSistema = UsuarioFixture.autenticado(UsuarioFixture.adminSistema(ID_ADMIN_SISTEMA));
        datosSucursal = new SucursalRequest("  " + NOMBRE + " ", "Quetzaltenango", "4a. Calle 12-35, Zona 1",
                "77651234", new BigDecimal("14.834500"), new BigDecimal("-91.518600"));

        buscador = new BuscadorIdentidad(usuarioRepository, sucursalRepository);
        sucursalService = new GestionSucursalesServiceImpl(sucursalRepository, new ValidadorSucursal(sucursalRepository),
                buscador, usuarioService, sucursalMapper, usuarioMapper, eventos);
    }

    @Test
    void crear_creaSucursalYAdministradorYPublicaSucursalCreada() {
        // Arrange
        CrearSucursalRequest request = new CrearSucursalRequest(datosSucursal,
                new NuevoAdministradorRequest(NOMBRE_ADMIN, CORREO_ADMIN));
        ArgumentCaptor<Sucursal> sucursalCaptor = ArgumentCaptor.forClass(Sucursal.class);
        when(sucursalRepository.existsByNombreIgnoreCase(NOMBRE)).thenReturn(false);
        when(sucursalRepository.saveAndFlush(any(Sucursal.class))).thenAnswer(inv -> {
            Sucursal s = inv.getArgument(0);
            s.setId(ID_SUCURSAL);
            s.setVersion(0L);
            return s;
        });
        when(usuarioService.registrarPersonal(eq(NOMBRE_ADMIN), eq(CORREO_ADMIN), eq(Rol.ADMIN_SUCURSAL),
                any(Sucursal.class), eq(ID_ADMIN_SISTEMA), eq(IP)))
                .thenAnswer(inv -> UsuarioFixture.adminSucursal(ID_ADMIN_SUCURSAL, inv.getArgument(3)));

        // Act
        SucursalCreadaResponse result = sucursalService.crear(request, adminSistema, IP);

        // Assert
        assertAll(
                () -> verify(sucursalRepository).saveAndFlush(sucursalCaptor.capture()),
                () -> assertEquals(NOMBRE, sucursalCaptor.getValue().getNombre()),
                () -> assertTrue(sucursalCaptor.getValue().getActiva()),
                () -> verify(eventos).sucursalCreada(sucursalCaptor.getValue()),
                () -> assertEquals(ID_SUCURSAL, result.sucursal().id()),
                () -> assertEquals(ID_ADMIN_SUCURSAL, result.administrador().id()),
                () -> assertEquals(ID_SUCURSAL, result.administrador().idSucursal()),
                () -> assertEquals(Rol.ADMIN_SUCURSAL, result.administrador().rol())
        );
    }

    @Test
    void crear_cuandoNombreDuplicado_lanzaConflicto() {
        // Arrange
        CrearSucursalRequest request = new CrearSucursalRequest(datosSucursal,
                new NuevoAdministradorRequest(NOMBRE_ADMIN, CORREO_ADMIN));
        when(sucursalRepository.existsByNombreIgnoreCase(NOMBRE)).thenReturn(true);

        // Assert
        assertThrows(NombreSucursalDuplicadoException.class,
                () -> sucursalService.crear(request, adminSistema, IP));
        verify(sucursalRepository, never()).saveAndFlush(any());
        verifyNoInteractions(eventos, usuarioService);
    }

    @Test
    void actualizar_cambiaDatosYPublicaSucursalActualizada() {
        // Arrange
        Sucursal sucursal = SucursalFixture.activa(ID_SUCURSAL);
        SucursalRequest datos = new SucursalRequest(NOMBRE_ACTUALIZADO, "Quetzaltenango", "Terminal Minerva",
                null, new BigDecimal("14.850000"), new BigDecimal("-91.530000"));
        when(sucursalRepository.findById(ID_SUCURSAL)).thenReturn(Optional.of(sucursal));
        when(sucursalRepository.existsByNombreIgnoreCaseAndIdNot(NOMBRE_ACTUALIZADO, ID_SUCURSAL)).thenReturn(false);
        when(sucursalRepository.saveAndFlush(sucursal)).thenAnswer(inv -> {
            sucursal.setVersion(1L);
            return sucursal;
        });

        // Act
        SucursalResponse result = sucursalService.actualizar(ID_SUCURSAL, new ActualizarSucursalRequest(datos, 0L));

        // Assert
        assertAll(
                () -> verify(eventos).sucursalActualizada(sucursal),
                () -> assertEquals(NOMBRE_ACTUALIZADO, result.nombre()),
                () -> assertEquals("Terminal Minerva", result.direccion()),
                () -> assertEquals(1L, result.version()),
                () -> assertEquals(ID_SUCURSAL, result.id())
        );
    }

    @Test
    void actualizar_cuandoVersionDesactualizada_lanzaConflicto() {
        // Arrange
        when(sucursalRepository.findById(ID_SUCURSAL)).thenReturn(Optional.of(SucursalFixture.activa(ID_SUCURSAL)));

        // Assert
        assertThrows(RegistroModificadoException.class,
                () -> sucursalService.actualizar(ID_SUCURSAL, new ActualizarSucursalRequest(datosSucursal, 5L)));
        verifyNoInteractions(eventos);
    }

    @Test
    void actualizar_cuandoNombreDuplicado_lanzaConflicto() {
        // Arrange
        when(sucursalRepository.findById(ID_SUCURSAL)).thenReturn(Optional.of(SucursalFixture.activa(ID_SUCURSAL)));
        when(sucursalRepository.existsByNombreIgnoreCaseAndIdNot(NOMBRE, ID_SUCURSAL)).thenReturn(true);

        // Assert
        assertThrows(NombreSucursalDuplicadoException.class,
                () -> sucursalService.actualizar(ID_SUCURSAL, new ActualizarSucursalRequest(datosSucursal, null)));
        verify(sucursalRepository, never()).saveAndFlush(any());
    }

    @Test
    void cambiarEstado_desactivaYPublicaSucursalActualizada() {
        // Arrange
        Sucursal sucursal = SucursalFixture.activa(ID_SUCURSAL);
        when(sucursalRepository.findById(ID_SUCURSAL)).thenReturn(Optional.of(sucursal));
        when(sucursalRepository.saveAndFlush(sucursal)).thenReturn(sucursal);

        // Act
        SucursalResponse result = sucursalService.cambiarEstado(ID_SUCURSAL, false);

        // Assert
        assertAll(
                () -> assertFalse(result.activa()),
                () -> verify(eventos).sucursalActualizada(sucursal)
        );
    }

    @Test
    void cambiarEstado_cuandoYaTieneEseEstado_noPublicaEvento() {
        // Arrange
        when(sucursalRepository.findById(ID_SUCURSAL)).thenReturn(Optional.of(SucursalFixture.activa(ID_SUCURSAL)));

        // Act
        SucursalResponse result = sucursalService.cambiarEstado(ID_SUCURSAL, true);

        // Assert
        assertAll(
                () -> assertTrue(result.activa()),
                () -> verify(sucursalRepository, never()).saveAndFlush(any()),
                () -> verifyNoInteractions(eventos)
        );
    }
}
