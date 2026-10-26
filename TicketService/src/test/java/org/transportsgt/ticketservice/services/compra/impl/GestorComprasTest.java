package org.transportsgt.ticketservice.services.compra.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.transportsgt.ticketservice.config.BoletosProperties;
import org.transportsgt.ticketservice.eventos.EventosBoletos;
import org.transportsgt.ticketservice.exception.AsientoNoDisponibleException;
import org.transportsgt.ticketservice.exception.CompraNoEncontradaException;
import org.transportsgt.ticketservice.fixture.CompraFixture;
import org.transportsgt.ticketservice.fixture.ViajeRefFixture;
import org.transportsgt.ticketservice.models.Boleto;
import org.transportsgt.ticketservice.models.Compra;
import org.transportsgt.ticketservice.models.EstadoBoleto;
import org.transportsgt.ticketservice.models.EstadoCompra;
import org.transportsgt.ticketservice.models.ViajeRef;
import org.transportsgt.ticketservice.repositories.BoletoRepository;
import org.transportsgt.ticketservice.repositories.CompraRepository;
import org.transportsgt.ticketservice.repositories.ViajeRefRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GestorComprasTest {

    private static final OffsetDateTime AHORA = OffsetDateTime.ofInstant(ViajeRefFixture.AHORA, ZoneOffset.UTC);

    @Mock
    private CompraRepository compraRepository;
    @Mock
    private BoletoRepository boletoRepository;
    @Mock
    private ViajeRefRepository viajeRefRepository;
    @Mock
    private EventosBoletos eventos;

    private GestorCompras gestor;
    private ViajeRef viaje;
    private UUID idCompra;
    private UUID idComprador;
    private UUID idPago;

    @BeforeEach
    void setUp() {
        BoletosProperties propiedades = new BoletosProperties(new BoletosProperties.Compra(10),
                new BoletosProperties.Reserva(Duration.ofMinutes(10)),
                new BoletosProperties.Recuperacion(Duration.ofMinutes(2), 60_000));
        gestor = new GestorCompras(compraRepository, boletoRepository, viajeRefRepository, eventos,
                new GeneradorCodigoBoleto(), propiedades, Clock.fixed(ViajeRefFixture.AHORA, ZoneOffset.UTC));
        viaje = ViajeRefFixture.programado(UUID.randomUUID());
        idCompra = UUID.randomUUID();
        idComprador = UUID.randomUUID();
        idPago = UUID.randomUUID();
    }

    private Compra nueva() {
        Compra compra = CompraFixture.reservada(idCompra, viaje, idComprador);
        compra.setEstado(null);
        compra.setReservaExpiraEn(null);
        compra.setVersion(null);
        return compra;
    }

    @Nested
    class Reservar {

        @Test
        void reservar_cuandoLosAsientosEstanLibres_guardaReservadaConVencimientoYBoletos() {
            // Arrange
            when(boletoRepository.findOcupadosEntre(viaje.getId(), List.of((short) 12, (short) 13))).thenReturn(List.of());
            when(compraRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            ArgumentCaptor<Compra> captor = ArgumentCaptor.forClass(Compra.class);

            // Act
            gestor.reservar(nueva(), List.of((short) 12, (short) 13));

            // Assert
            verify(compraRepository).saveAndFlush(captor.capture());
            Compra guardada = captor.getValue();
            assertAll(
                    () -> assertEquals(EstadoCompra.RESERVADA, guardada.getEstado()),
                    () -> assertEquals(AHORA.plusMinutes(10), guardada.getReservaExpiraEn()),
                    () -> assertEquals(2, guardada.getBoletos().size()),
                    () -> assertTrue(guardada.getBoletos().stream().allMatch(b -> b.getEstado() == EstadoBoleto.RESERVADO)),
                    () -> assertTrue(guardada.getBoletos().stream().allMatch(b -> b.getCodigo().matches("BOL-[A-Z2-9]{8}"))),
                    () -> assertEquals(List.of((short) 12, (short) 13),
                            guardada.getBoletos().stream().map(Boleto::getNumeroAsiento).toList())
            );
            verifyNoInteractions(eventos);
        }

        @Test
        void reservar_cuandoUnAsientoYaEstaOcupado_lanzaAsientoNoDisponibleConElAsiento() {
            // Arrange
            when(boletoRepository.findOcupadosEntre(viaje.getId(), List.of((short) 12))).thenReturn(List.of((short) 12));

            // Act
            AsientoNoDisponibleException ex = assertThrows(AsientoNoDisponibleException.class,
                    () -> gestor.reservar(nueva(), List.of((short) 12)));

            // Assert
            assertEquals("El asiento 12 ya está reservado o vendido", ex.getMessage());
            verify(compraRepository, never()).saveAndFlush(any());
        }

        @Test
        void reservar_cuandoOtraCompraGanaLaCarrera_lanzaAsientoNoDisponible() {
            // Arrange
            when(boletoRepository.findOcupadosEntre(any(), any())).thenReturn(List.of());
            when(compraRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("insert",
                    new RuntimeException("duplicate key value violates unique constraint \"uq_asiento_activo\"")));

            // Act & Assert
            AsientoNoDisponibleException ex = assertThrows(AsientoNoDisponibleException.class,
                    () -> gestor.reservar(nueva(), List.of((short) 12)));
            assertEquals("ASIENTO_NO_DISPONIBLE", ex.getCodigo());
        }

        @Test
        void reservar_cuandoFallaOtraRestriccion_propagaLaExcepcion() {
            // Arrange
            when(boletoRepository.findOcupadosEntre(any(), any())).thenReturn(List.of());
            when(compraRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("insert",
                    new RuntimeException("duplicate key value violates unique constraint \"compra_pkey\"")));

            // Act & Assert
            assertThrows(DataIntegrityViolationException.class, () -> gestor.reservar(nueva(), List.of((short) 12)));
        }
    }

    @Nested
    class RegistrarConfirmada {

        @Test
        void registrarConfirmada_guardaConfirmadaConBoletosConfirmadosYPublicaLaVenta() {
            // Arrange
            UUID idRuta = UUID.randomUUID();
            when(boletoRepository.findOcupadosEntre(any(), any())).thenReturn(List.of());
            when(compraRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

            // Act
            Compra guardada = gestor.registrarConfirmada(nueva(), List.of((short) 20), idRuta);

            // Assert
            assertAll(
                    () -> assertEquals(EstadoCompra.CONFIRMADA, guardada.getEstado()),
                    () -> assertEquals(AHORA, guardada.getConfirmadoEn()),
                    () -> assertEquals(EstadoBoleto.CONFIRMADO, guardada.getBoletos().getFirst().getEstado())
            );
            verify(eventos).ventaRegistrada(guardada, idRuta);
        }
    }

    @Nested
    class Confirmar {

        @Test
        void confirmar_cuandoEstaReservada_laConfirmaConElPagoYPublicaLaVenta() {
            // Arrange
            Compra compra = CompraFixture.reservada(idCompra, viaje, idComprador, 12);
            when(compraRepository.findByIdParaActualizar(idCompra)).thenReturn(Optional.of(compra));
            when(compraRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            when(viajeRefRepository.findById(viaje.getId())).thenReturn(Optional.of(viaje));

            // Act
            GestorCompras.Confirmacion resultado = gestor.confirmar(idCompra, idPago);

            // Assert
            assertAll(
                    () -> assertFalse(resultado.anulada()),
                    () -> assertEquals(EstadoCompra.CONFIRMADA, compra.getEstado()),
                    () -> assertEquals(idPago, compra.getIdPago()),
                    () -> assertEquals(AHORA, compra.getConfirmadoEn()),
                    () -> assertEquals(EstadoBoleto.CONFIRMADO, compra.getBoletos().getFirst().getEstado())
            );
            verify(eventos).ventaRegistrada(compra, viaje.getIdRuta());
        }

        @Test
        void confirmar_cuandoYaEstabaConfirmada_noCambiaNada() {
            // Arrange
            Compra compra = CompraFixture.confirmada(idCompra, viaje, idComprador, 12);
            when(compraRepository.findByIdParaActualizar(idCompra)).thenReturn(Optional.of(compra));

            // Act
            GestorCompras.Confirmacion resultado = gestor.confirmar(idCompra, idPago);

            // Assert
            assertAll(
                    () -> assertFalse(resultado.anulada()),
                    () -> assertSame(compra, resultado.compra())
            );
            verify(compraRepository, never()).saveAndFlush(any());
            verifyNoInteractions(eventos);
        }

        @Test
        void confirmar_cuandoElViajeSeEliminoMientrasSeCobraba_guardaElPagoYRepublicaLaAnulacion() {
            // Arrange
            Compra compra = CompraFixture.anulada(idCompra, viaje, idComprador, 12);
            when(compraRepository.findByIdParaActualizar(idCompra)).thenReturn(Optional.of(compra));
            when(compraRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

            // Act
            GestorCompras.Confirmacion resultado = gestor.confirmar(idCompra, idPago);

            // Assert
            assertAll(
                    () -> assertTrue(resultado.anulada()),
                    () -> assertEquals(EstadoCompra.ANULADA, compra.getEstado()),
                    () -> assertEquals(idPago, compra.getIdPago())
            );
            verify(eventos).boletosAnulados(compra);
        }

        @Test
        void confirmar_cuandoLaAnuladaYaTeniaElPago_noRepublica() {
            // Arrange
            Compra compra = CompraFixture.anulada(idCompra, viaje, idComprador, 12);
            compra.setIdPago(idPago);
            when(compraRepository.findByIdParaActualizar(idCompra)).thenReturn(Optional.of(compra));

            // Act
            GestorCompras.Confirmacion resultado = gestor.confirmar(idCompra, idPago);

            // Assert
            assertTrue(resultado.anulada());
            verifyNoInteractions(eventos);
        }

        @Test
        void confirmar_cuandoLaCompraNoExiste_lanzaCompraNoEncontrada() {
            // Arrange
            when(compraRepository.findByIdParaActualizar(idCompra)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(CompraNoEncontradaException.class, () -> gestor.confirmar(idCompra, idPago));
        }

        @Test
        void confirmar_cuandoElViajeYaNoEstaEnLaProyeccion_publicaSinRuta() {
            // Arrange
            Compra compra = CompraFixture.reservada(idCompra, viaje, idComprador, 12);
            when(compraRepository.findByIdParaActualizar(idCompra)).thenReturn(Optional.of(compra));
            when(compraRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            when(viajeRefRepository.findById(viaje.getId())).thenReturn(Optional.empty());

            // Act
            gestor.confirmar(idCompra, idPago);

            // Assert
            verify(eventos).ventaRegistrada(compra, null);
        }
    }

    @Nested
    class Liberar {

        @Test
        void liberar_cuandoEstaReservada_laAnulaSinPublicar() {
            // Arrange
            Compra compra = CompraFixture.reservada(idCompra, viaje, idComprador, 12);
            when(compraRepository.findByIdParaActualizar(idCompra)).thenReturn(Optional.of(compra));
            when(compraRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

            // Act
            gestor.liberar(idCompra, "SALDO_INSUFICIENTE");

            // Assert
            assertAll(
                    () -> assertEquals(EstadoCompra.ANULADA, compra.getEstado()),
                    () -> assertEquals("SALDO_INSUFICIENTE", compra.getMotivoAnulacion()),
                    () -> assertEquals(AHORA, compra.getAnuladoEn()),
                    () -> assertEquals(EstadoBoleto.ANULADO, compra.getBoletos().getFirst().getEstado())
            );
            verifyNoInteractions(eventos);
        }

        @Test
        void liberar_cuandoYaNoEstaReservada_noCambiaNada() {
            // Arrange
            Compra compra = CompraFixture.confirmada(idCompra, viaje, idComprador, 12);
            when(compraRepository.findByIdParaActualizar(idCompra)).thenReturn(Optional.of(compra));

            // Act
            gestor.liberar(idCompra, GestorCompras.MOTIVO_RESERVA_VENCIDA);

            // Assert
            assertAll(
                    () -> assertEquals(EstadoCompra.CONFIRMADA, compra.getEstado()),
                    () -> assertNull(compra.getMotivoAnulacion())
            );
            verify(compraRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class AnularPorViaje {

        @Test
        void anularPorViaje_cuandoEstaConfirmada_laAnulaYPublicaBoletosAnulados() {
            // Arrange
            Compra compra = CompraFixture.confirmada(idCompra, viaje, idComprador, 12, 13);
            when(compraRepository.findByIdParaActualizar(idCompra)).thenReturn(Optional.of(compra));
            when(compraRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

            // Act
            boolean anulada = gestor.anularPorViaje(idCompra);

            // Assert
            assertAll(
                    () -> assertTrue(anulada),
                    () -> assertEquals(EstadoCompra.ANULADA, compra.getEstado()),
                    () -> assertEquals(GestorCompras.MOTIVO_VIAJE_ELIMINADO, compra.getMotivoAnulacion()),
                    () -> assertNotNull(compra.getAnuladoEn()),
                    () -> assertTrue(compra.getBoletos().stream().allMatch(b -> b.getEstado() == EstadoBoleto.ANULADO))
            );
            verify(eventos).boletosAnulados(compra);
        }

        @Test
        void anularPorViaje_cuandoYaEstabaAnulada_noRepublica() {
            // Arrange
            when(compraRepository.findByIdParaActualizar(idCompra))
                    .thenReturn(Optional.of(CompraFixture.anulada(idCompra, viaje, idComprador, 12)));

            // Act & Assert
            assertFalse(gestor.anularPorViaje(idCompra));
            verifyNoInteractions(eventos);
        }
    }
}
