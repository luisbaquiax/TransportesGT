package org.transportsgt.ticketservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.ticketservice.config.BoletosProperties;
import org.transportsgt.ticketservice.exception.AsientoInvalidoException;
import org.transportsgt.ticketservice.exception.CompraDuplicadaException;
import org.transportsgt.ticketservice.exception.CuentaInactivaException;
import org.transportsgt.ticketservice.exception.PerfilIncompletoException;
import org.transportsgt.ticketservice.exception.ViajeNoDisponibleException;
import org.transportsgt.ticketservice.fixture.ClienteRefFixture;
import org.transportsgt.ticketservice.fixture.CompraFixture;
import org.transportsgt.ticketservice.fixture.ViajeRefFixture;
import org.transportsgt.ticketservice.models.ClienteRef;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.ClienteRefRepository;
import org.transportsgt.ticketservice.repositories.ViajeRefRepository;
import org.transportsgt.eventos.payload.Canal;
import org.transportsgt.eventos.payload.EstadoViaje;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidadorCompraTest {

    @Mock
    private ClienteRefRepository clienteRefRepository;
    @Mock
    private ViajeRefRepository viajeRefRepository;

    private ValidadorCompra validador;
    private ViajeRef viaje;
    private UUID idUsuario;

    @BeforeEach
    void setUp() {
        BoletosProperties propiedades = new BoletosProperties(new BoletosProperties.Compra(3),
                new BoletosProperties.Reserva(Duration.ofMinutes(10)),
                new BoletosProperties.Recuperacion(Duration.ofMinutes(2), 60_000));
        validador = new ValidadorCompra(clienteRefRepository, viajeRefRepository, propiedades,
                Clock.fixed(ViajeRefFixture.AHORA, ZoneOffset.UTC));
        viaje = ViajeRefFixture.programado(UUID.randomUUID());
        idUsuario = UUID.randomUUID();
    }

    @Nested
    class ValidarComprador {

        @Test
        void validarComprador_cuandoTieneNitYEstaActivo_devuelveElPerfil() {
            // Arrange
            ClienteRef cliente = ClienteRefFixture.cliente(idUsuario);
            when(clienteRefRepository.findById(idUsuario)).thenReturn(Optional.of(cliente));

            // Act & Assert
            assertSame(cliente, validador.validarComprador(idUsuario));
        }

        @Test
        void validarComprador_cuandoNoHayPerfilOFaltaElNit_lanzaPerfilIncompleto() {
            // Arrange
            ClienteRef enBlanco = ClienteRefFixture.cliente(idUsuario);
            enBlanco.setNit(" ");
            UUID sinPerfil = UUID.randomUUID();
            UUID sinNit = UUID.randomUUID();
            UUID conNitEnBlanco = UUID.randomUUID();
            when(clienteRefRepository.findById(sinPerfil)).thenReturn(Optional.empty());
            when(clienteRefRepository.findById(sinNit)).thenReturn(Optional.of(ClienteRefFixture.sinNit(sinNit)));
            when(clienteRefRepository.findById(conNitEnBlanco)).thenReturn(Optional.of(enBlanco));

            // Act & Assert
            assertAll(
                    () -> assertThrows(PerfilIncompletoException.class, () -> validador.validarComprador(sinPerfil)),
                    () -> assertThrows(PerfilIncompletoException.class, () -> validador.validarComprador(sinNit)),
                    () -> assertThrows(PerfilIncompletoException.class, () -> validador.validarComprador(conNitEnBlanco))
            );
        }

        @Test
        void validarComprador_cuandoEstaInactivo_lanzaCuentaInactiva() {
            // Arrange
            when(clienteRefRepository.findById(idUsuario)).thenReturn(Optional.of(ClienteRefFixture.inactivo(idUsuario)));

            // Act & Assert
            assertThrows(CuentaInactivaException.class, () -> validador.validarComprador(idUsuario));
        }
    }

    @Nested
    class ValidarViaje {

        @Test
        void validarViaje_cuandoEstaProgramadoYSaleDespues_loDevuelve() {
            // Arrange
            when(viajeRefRepository.findById(viaje.getId())).thenReturn(Optional.of(viaje));

            // Act & Assert
            assertSame(viaje, validador.validarViaje(viaje.getId()));
        }

        @Test
        void validarViaje_cuandoNoExisteOFueEliminado_lanzaViajeNoDisponible() {
            // Arrange
            UUID inexistente = UUID.randomUUID();
            viaje.setEliminado(true);
            when(viajeRefRepository.findById(inexistente)).thenReturn(Optional.empty());
            when(viajeRefRepository.findById(viaje.getId())).thenReturn(Optional.of(viaje));

            // Act & Assert
            assertAll(
                    () -> assertThrows(ViajeNoDisponibleException.class, () -> validador.validarViaje(inexistente)),
                    () -> assertEquals("El viaje fue cancelado", assertThrows(ViajeNoDisponibleException.class,
                            () -> validador.validarViaje(viaje.getId())).getMessage())
            );
        }

        @Test
        void validarViaje_cuandoNoEstaProgramado_lanzaViajeNoDisponible() {
            // Arrange
            viaje.setEstado(EstadoViaje.EN_TRANSITO);
            when(viajeRefRepository.findById(viaje.getId())).thenReturn(Optional.of(viaje));

            // Act & Assert
            assertThrows(ViajeNoDisponibleException.class, () -> validador.validarViaje(viaje.getId()));
        }

        @Test
        void validarViaje_cuandoYaSalio_lanzaViajeNoDisponible() {
            // Arrange
            viaje.setFechaHoraSalida(OffsetDateTime.ofInstant(ViajeRefFixture.AHORA, ZoneOffset.UTC));
            when(viajeRefRepository.findById(viaje.getId())).thenReturn(Optional.of(viaje));

            // Act & Assert
            assertEquals("El viaje ya salió", assertThrows(ViajeNoDisponibleException.class,
                    () -> validador.validarViaje(viaje.getId())).getMessage());
        }

        @Test
        void validarViaje_cuandoNoTieneBus_lanzaViajeNoDisponible() {
            // Arrange
            viaje.setCapacidadBus(null);
            when(viajeRefRepository.findById(viaje.getId())).thenReturn(Optional.of(viaje));

            // Act & Assert
            assertThrows(ViajeNoDisponibleException.class, () -> validador.validarViaje(viaje.getId()));
        }
    }

    @Nested
    class ValidarAsientos {

        @Test
        void validarAsientos_cuandoSonValidos_losDevuelveOrdenados() {
            assertEquals(List.of((short) 1, (short) 13, (short) 40), validador.validarAsientos(List.of(40, 1, 13), viaje));
        }

        @Test
        void validarAsientos_cuandoNoHayOPasanDelMaximo_lanzaAsientoInvalido() {
            assertAll(
                    () -> assertThrows(AsientoInvalidoException.class, () -> validador.validarAsientos(List.of(), viaje)),
                    () -> assertThrows(AsientoInvalidoException.class, () -> validador.validarAsientos(null, viaje)),
                    () -> assertEquals("Máximo 3 asientos por compra", assertThrows(AsientoInvalidoException.class,
                            () -> validador.validarAsientos(List.of(1, 2, 3, 4), viaje)).getMessage())
            );
        }

        @Test
        void validarAsientos_cuandoHayRepetidosOFueraDeLaCapacidad_lanzaAsientoInvalido() {
            assertAll(
                    () -> assertEquals("El asiento 5 está repetido", assertThrows(AsientoInvalidoException.class,
                            () -> validador.validarAsientos(List.of(5, 5), viaje)).getMessage()),
                    () -> assertThrows(AsientoInvalidoException.class, () -> validador.validarAsientos(List.of(0), viaje)),
                    () -> assertThrows(AsientoInvalidoException.class, () -> validador.validarAsientos(List.of(41), viaje)),
                    () -> assertThrows(AsientoInvalidoException.class,
                            () -> validador.validarAsientos(Arrays.asList(1, null), viaje))
            );
        }
    }

    @Nested
    class VerificarRepetida {

        @Test
        void verificarRepetida_cuandoCoincidenTodosLosDatos_noLanza() {
            // Arrange
            Compra enLinea = CompraFixture.reservada(UUID.randomUUID(), viaje, idUsuario, 12, 13);
            Compra mostrador = CompraFixture.deMostrador(UUID.randomUUID(), viaje, idUsuario, 20);

            // Act & Assert
            assertAll(
                    () -> assertDoesNotThrow(() -> validador.verificarRepetida(enLinea, Canal.EN_LINEA, idUsuario,
                            viaje.getId(), List.of(13, 12))),
                    () -> assertDoesNotThrow(() -> validador.verificarRepetida(mostrador, Canal.MOSTRADOR, idUsuario,
                            viaje.getId(), List.of(20)))
            );
        }

        @Test
        void verificarRepetida_cuandoCambiaUsuarioViajeAsientosOCanal_lanzaCompraDuplicada() {
            // Arrange
            Compra compra = CompraFixture.reservada(UUID.randomUUID(), viaje, idUsuario, 12);

            // Act & Assert
            assertAll(
                    () -> assertThrows(CompraDuplicadaException.class, () -> validador.verificarRepetida(compra,
                            Canal.EN_LINEA, UUID.randomUUID(), viaje.getId(), List.of(12))),
                    () -> assertThrows(CompraDuplicadaException.class, () -> validador.verificarRepetida(compra,
                            Canal.EN_LINEA, idUsuario, UUID.randomUUID(), List.of(12))),
                    () -> assertThrows(CompraDuplicadaException.class, () -> validador.verificarRepetida(compra,
                            Canal.EN_LINEA, idUsuario, viaje.getId(), List.of(14))),
                    () -> assertThrows(CompraDuplicadaException.class, () -> validador.verificarRepetida(compra,
                            Canal.EN_LINEA, idUsuario, viaje.getId(), null)),
                    () -> assertThrows(CompraDuplicadaException.class, () -> validador.verificarRepetida(compra,
                            Canal.MOSTRADOR, idUsuario, viaje.getId(), List.of(12)))
            );
        }
    }
}
