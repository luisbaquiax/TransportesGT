package org.transportsgt.customerwalletservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.customerwalletservice.config.security.UsuarioAutenticado;
import org.transportsgt.customerwalletservice.dto.request.ActualizarPerfilRequest;
import org.transportsgt.customerwalletservice.dto.response.ClienteBusquedaResponse;
import org.transportsgt.customerwalletservice.dto.response.ClienteResponse;
import org.transportsgt.customerwalletservice.eventos.EventosCartera;
import org.transportsgt.customerwalletservice.exception.ClienteNoEncontradoException;
import org.transportsgt.customerwalletservice.exception.DpiDuplicadoException;
import org.transportsgt.customerwalletservice.exception.NitDuplicadoException;
import org.transportsgt.customerwalletservice.exception.NitInvalidoException;
import org.transportsgt.customerwalletservice.fixture.CarteraFixture;
import org.transportsgt.customerwalletservice.fixture.ClienteFixture;
import org.transportsgt.customerwalletservice.fixture.EventoFixture;
import org.transportsgt.customerwalletservice.mappers.ClienteMapperImpl;
import org.transportsgt.customerwalletservice.models.Cartera;
import org.transportsgt.customerwalletservice.models.Cliente;
import org.transportsgt.customerwalletservice.repositories.CarteraRepository;
import org.transportsgt.customerwalletservice.repositories.ClienteRepository;
import org.transportsgt.eventos.payload.DatosUsuario;
import org.transportsgt.eventos.payload.Rol;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteServiceImplTest {

    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private CarteraRepository carteraRepository;
    @Mock
    private EventosCartera eventos;

    private ClienteServiceImpl servicio;
    private UUID idUsuario;
    private Cliente cliente;
    private UsuarioAutenticado usuario;

    @BeforeEach
    void setUp() {
        servicio = new ClienteServiceImpl(clienteRepository, carteraRepository, new ClienteMapperImpl(), eventos);
        idUsuario = UUID.randomUUID();
        cliente = ClienteFixture.cliente(idUsuario);
        usuario = ClienteFixture.usuario(idUsuario);
    }

    @Nested
    class AplicarUsuario {

        @Test
        void aplicarUsuario_cuandoNoExiste_creaClienteYCarteraYPublicaPerfil() {
            // Arrange
            DatosUsuario datos = EventoFixture.usuario(idUsuario);
            when(clienteRepository.findByIdUsuario(idUsuario)).thenReturn(Optional.empty());
            when(clienteRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            ArgumentCaptor<Cliente> clienteCaptor = ArgumentCaptor.forClass(Cliente.class);
            ArgumentCaptor<Cartera> carteraCaptor = ArgumentCaptor.forClass(Cartera.class);

            // Act
            boolean aplicado = servicio.aplicarUsuario(datos, 2L);

            // Assert
            verify(clienteRepository).saveAndFlush(clienteCaptor.capture());
            verify(carteraRepository).save(carteraCaptor.capture());
            Cliente creado = clienteCaptor.getValue();
            assertAll(
                    () -> assertTrue(aplicado),
                    () -> assertEquals(idUsuario, creado.getIdUsuario()),
                    () -> assertEquals(ClienteFixture.NOMBRE, creado.getNombreCompleto()),
                    () -> assertEquals(ClienteFixture.CORREO, creado.getCorreo()),
                    () -> assertTrue(creado.getActivo()),
                    () -> assertNull(creado.getNit()),
                    () -> assertEquals(2L, creado.getVersionUsuario()),
                    () -> assertSame(creado, carteraCaptor.getValue().getCliente()),
                    () -> assertEquals(0, BigDecimal.ZERO.compareTo(carteraCaptor.getValue().getSaldo()))
            );
            verify(eventos).perfilClienteActualizado(creado);
        }

        @Test
        void aplicarUsuario_cuandoVersionEsNula_creaConVersionCero() {
            // Arrange
            when(clienteRepository.findByIdUsuario(idUsuario)).thenReturn(Optional.empty());
            when(clienteRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);

            // Act
            servicio.aplicarUsuario(EventoFixture.usuario(idUsuario), null);

            // Assert
            verify(clienteRepository).saveAndFlush(captor.capture());
            assertEquals(0L, captor.getValue().getVersionUsuario());
        }

        @Test
        void aplicarUsuario_cuandoVersionNoEsMayor_loIgnora() {
            // Arrange
            cliente.setVersionUsuario(3L);
            when(clienteRepository.findByIdUsuario(idUsuario)).thenReturn(Optional.of(cliente));

            // Act
            boolean aplicado = servicio.aplicarUsuario(EventoFixture.usuario(idUsuario, "Otro nombre", false), 3L);

            // Assert
            assertAll(
                    () -> assertFalse(aplicado),
                    () -> assertEquals(ClienteFixture.NOMBRE, cliente.getNombreCompleto()),
                    () -> assertTrue(cliente.getActivo())
            );
            verify(clienteRepository, never()).saveAndFlush(any());
            verifyNoInteractions(eventos, carteraRepository);
        }

        @Test
        void aplicarUsuario_cuandoCambiaNombreOActivo_actualizaYPublica() {
            // Arrange
            when(clienteRepository.findByIdUsuario(idUsuario)).thenReturn(Optional.of(cliente));
            when(clienteRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

            // Act
            boolean aplicado = servicio.aplicarUsuario(EventoFixture.usuario(idUsuario, "Carlos M. López", false), 4L);

            // Assert
            assertAll(
                    () -> assertTrue(aplicado),
                    () -> assertEquals("Carlos M. López", cliente.getNombreCompleto()),
                    () -> assertFalse(cliente.getActivo()),
                    () -> assertEquals(4L, cliente.getVersionUsuario())
            );
            verify(eventos).perfilClienteActualizado(cliente);
            verifyNoInteractions(carteraRepository);
        }

        @Test
        void aplicarUsuario_cuandoSoloCambiaElCorreo_actualizaSinPublicar() {
            // Arrange
            DatosUsuario datos = new DatosUsuario(idUsuario, "nuevo@correo.gt", ClienteFixture.NOMBRE,
                    Rol.CLIENTE, null, true);
            when(clienteRepository.findByIdUsuario(idUsuario)).thenReturn(Optional.of(cliente));
            when(clienteRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

            // Act
            boolean aplicado = servicio.aplicarUsuario(datos, 1L);

            // Assert
            assertAll(
                    () -> assertTrue(aplicado),
                    () -> assertEquals("nuevo@correo.gt", cliente.getCorreo()),
                    () -> assertEquals(1L, cliente.getVersionUsuario())
            );
            verify(eventos, never()).perfilClienteActualizado(any());
        }
    }

    @Nested
    class ObtenerPerfil {

        @Test
        void obtenerPerfil_cuandoExiste_devuelvePerfilConSaldo() {
            // Arrange
            Cartera cartera = CarteraFixture.cartera(cliente, "350.00");
            when(clienteRepository.findByIdUsuario(idUsuario)).thenReturn(Optional.of(cliente));
            when(carteraRepository.findByClienteIdUsuario(idUsuario)).thenReturn(Optional.of(cartera));

            // Act
            ClienteResponse perfil = servicio.obtenerPerfil(usuario);

            // Assert
            assertAll(
                    () -> assertEquals(idUsuario, perfil.idUsuario()),
                    () -> assertEquals(ClienteFixture.NIT, perfil.nit()),
                    () -> assertEquals(new BigDecimal("350.00"), perfil.saldo())
            );
        }

        @Test
        void obtenerPerfil_cuandoNoTieneCartera_devuelveSaldoCero() {
            // Arrange
            when(clienteRepository.findByIdUsuario(idUsuario)).thenReturn(Optional.of(cliente));
            when(carteraRepository.findByClienteIdUsuario(idUsuario)).thenReturn(Optional.empty());

            // Act
            ClienteResponse perfil = servicio.obtenerPerfil(usuario);

            // Assert
            assertEquals(BigDecimal.ZERO, perfil.saldo());
        }

        @Test
        void obtenerPerfil_cuandoNoExiste_lanzaClienteNoEncontrado() {
            // Arrange
            when(clienteRepository.findByIdUsuario(idUsuario)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ClienteNoEncontradoException.class, () -> servicio.obtenerPerfil(usuario));
        }
    }

    @Nested
    class ActualizarPerfil {

        @Test
        void actualizarPerfil_cuandoCambiaElNit_loNormalizaYPublica() {
            // Arrange
            cliente.setNit(null);
            ActualizarPerfilRequest request = new ActualizarPerfilRequest(" 1234567-k ", "2456789010101",
                    "55551234", "  Zona 1, Xela ");
            when(clienteRepository.findByIdUsuario(idUsuario)).thenReturn(Optional.of(cliente));
            when(clienteRepository.existsByNitAndIdNot("1234567K", cliente.getId())).thenReturn(false);
            when(clienteRepository.existsByDpiAndIdNot("2456789010101", cliente.getId())).thenReturn(false);
            when(clienteRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            when(carteraRepository.findByClienteIdUsuario(idUsuario)).thenReturn(Optional.empty());
            ArgumentCaptor<Cliente> captor = ArgumentCaptor.forClass(Cliente.class);

            // Act
            ClienteResponse perfil = servicio.actualizarPerfil(usuario, request);

            // Assert
            verify(clienteRepository).saveAndFlush(captor.capture());
            Cliente guardado = captor.getValue();
            assertAll(
                    () -> assertEquals("1234567K", guardado.getNit()),
                    () -> assertEquals("2456789010101", guardado.getDpi()),
                    () -> assertEquals("55551234", guardado.getTelefono()),
                    () -> assertEquals("Zona 1, Xela", guardado.getDireccion()),
                    () -> assertEquals("1234567K", perfil.nit())
            );
            verify(eventos).perfilClienteActualizado(guardado);
        }

        @Test
        void actualizarPerfil_cuandoElNitNoCambia_noPublica() {
            // Arrange
            ActualizarPerfilRequest request = new ActualizarPerfilRequest(ClienteFixture.NIT, null, " ", null);
            when(clienteRepository.findByIdUsuario(idUsuario)).thenReturn(Optional.of(cliente));
            when(clienteRepository.existsByNitAndIdNot(ClienteFixture.NIT, cliente.getId())).thenReturn(false);
            when(clienteRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            when(carteraRepository.findByClienteIdUsuario(idUsuario)).thenReturn(Optional.empty());

            // Act
            servicio.actualizarPerfil(usuario, request);

            // Assert
            assertAll(
                    () -> assertNull(cliente.getDpi()),
                    () -> assertNull(cliente.getTelefono())
            );
            verify(eventos, never()).perfilClienteActualizado(any());
        }

        @Test
        void actualizarPerfil_cuandoQuitaElNit_loGuardaNuloYPublica() {
            // Arrange
            when(clienteRepository.findByIdUsuario(idUsuario)).thenReturn(Optional.of(cliente));
            when(clienteRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            when(carteraRepository.findByClienteIdUsuario(idUsuario)).thenReturn(Optional.empty());

            // Act
            servicio.actualizarPerfil(usuario, new ActualizarPerfilRequest("  ", null, null, null));

            // Assert
            assertNull(cliente.getNit());
            verify(eventos).perfilClienteActualizado(cliente);
        }

        @Test
        void actualizarPerfil_cuandoNitEsCF_lanzaNitInvalido() {
            // Arrange
            when(clienteRepository.findByIdUsuario(idUsuario)).thenReturn(Optional.of(cliente));

            // Act & Assert
            assertThrows(NitInvalidoException.class,
                    () -> servicio.actualizarPerfil(usuario, new ActualizarPerfilRequest("c-f", null, null, null)));
            verify(clienteRepository, never()).saveAndFlush(any());
        }

        @Test
        void actualizarPerfil_cuandoNitTieneFormatoInvalido_lanzaNitInvalido() {
            // Arrange
            when(clienteRepository.findByIdUsuario(idUsuario)).thenReturn(Optional.of(cliente));

            // Act & Assert
            assertThrows(NitInvalidoException.class,
                    () -> servicio.actualizarPerfil(usuario, new ActualizarPerfilRequest("12.34#", null, null, null)));
        }

        @Test
        void actualizarPerfil_cuandoNitEsDeOtroCliente_lanzaNitDuplicado() {
            // Arrange
            when(clienteRepository.findByIdUsuario(idUsuario)).thenReturn(Optional.of(cliente));
            when(clienteRepository.existsByNitAndIdNot("9876543", cliente.getId())).thenReturn(true);

            // Act & Assert
            assertThrows(NitDuplicadoException.class,
                    () -> servicio.actualizarPerfil(usuario, new ActualizarPerfilRequest("9876543", null, null, null)));
            verify(clienteRepository, never()).saveAndFlush(any());
        }

        @Test
        void actualizarPerfil_cuandoDpiEsDeOtroCliente_lanzaDpiDuplicado() {
            // Arrange
            when(clienteRepository.findByIdUsuario(idUsuario)).thenReturn(Optional.of(cliente));
            when(clienteRepository.existsByNitAndIdNot(anyString(), any())).thenReturn(false);
            when(clienteRepository.existsByDpiAndIdNot(ClienteFixture.DPI, cliente.getId())).thenReturn(true);

            // Act & Assert
            assertThrows(DpiDuplicadoException.class, () -> servicio.actualizarPerfil(usuario,
                    new ActualizarPerfilRequest(ClienteFixture.NIT, ClienteFixture.DPI, null, null)));
        }

        @Test
        void actualizarPerfil_cuandoNoExiste_lanzaClienteNoEncontrado() {
            // Arrange
            when(clienteRepository.findByIdUsuario(idUsuario)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ClienteNoEncontradoException.class,
                    () -> servicio.actualizarPerfil(usuario, new ActualizarPerfilRequest(null, null, null, null)));
        }
    }

    @Nested
    class BuscarPorNit {

        @Test
        void buscarPorNit_cuandoExiste_normalizaYDevuelveDatosDeMostrador() {
            // Arrange
            when(clienteRepository.findByNit("1234567K")).thenReturn(Optional.of(cliente));

            // Act
            ClienteBusquedaResponse encontrado = servicio.buscarPorNit("1234567-k");

            // Assert
            assertAll(
                    () -> assertEquals(idUsuario, encontrado.idUsuario()),
                    () -> assertEquals(ClienteFixture.NOMBRE, encontrado.nombreCompleto()),
                    () -> assertEquals(ClienteFixture.NIT, encontrado.nit())
            );
        }

        @Test
        void buscarPorNit_cuandoNoExiste_lanzaClienteNoEncontrado() {
            // Arrange
            when(clienteRepository.findByNit("9999999")).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(ClienteNoEncontradoException.class, () -> servicio.buscarPorNit("9999999"));
        }

        @Test
        void buscarPorNit_cuandoEstaVacio_lanzaClienteNoEncontradoSinConsultar() {
            // Act & Assert
            assertThrows(ClienteNoEncontradoException.class, () -> servicio.buscarPorNit(" - "));
            verifyNoInteractions(clienteRepository);
        }
    }
}
