package org.transportsgt.customerwalletservice.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.customerwalletservice.eventos.EventosCartera;
import org.transportsgt.customerwalletservice.fixture.CarteraFixture;
import org.transportsgt.customerwalletservice.fixture.ClienteFixture;
import org.transportsgt.customerwalletservice.fixture.EventoFixture;
import org.transportsgt.customerwalletservice.models.Cartera;
import org.transportsgt.customerwalletservice.models.MovimientoCartera;
import org.transportsgt.customerwalletservice.models.TipoMovimiento;
import org.transportsgt.customerwalletservice.repositories.CarteraRepository;
import org.transportsgt.customerwalletservice.repositories.MovimientoCarteraRepository;
import org.transportsgt.customerwalletservice.utils.Dinero;
import org.transportsgt.eventos.EventoNoRecuperableException;
import org.transportsgt.eventos.payload.TipoReferencia;

import java.math.BigDecimal;
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
class ReembolsoServiceImplTest {

    @Mock
    private CarteraRepository carteraRepository;
    @Mock
    private MovimientoCarteraRepository movimientoRepository;
    @Mock
    private EventosCartera eventos;

    private ReembolsoServiceImpl servicio;
    private Cartera cartera;
    private UUID idCompra;
    private MovimientoCartera pago;

    @BeforeEach
    void setUp() {
        servicio = new ReembolsoServiceImpl(carteraRepository, movimientoRepository, eventos,
                new ReglasCartera(new Dinero()));
        // El reembolso se aplica aunque el cliente esté inactivo
        cartera = CarteraFixture.cartera(ClienteFixture.inactivo(UUID.randomUUID()), "30.00");
        idCompra = UUID.randomUUID();
        pago = CarteraFixture.pago(cartera, TipoReferencia.BOLETOS, idCompra, "170.00");
    }

    @Nested
    class Reembolsar {

        @Test
        void reembolsar_cuandoHayPagoPorId_devuelveElMontoYPublica() {
            // Arrange
            when(movimientoRepository.findByIdAndTipo(pago.getId(), TipoMovimiento.PAGO)).thenReturn(Optional.of(pago));
            when(movimientoRepository.findByTipoAndTipoReferenciaAndIdReferencia(
                    TipoMovimiento.REEMBOLSO, TipoReferencia.BOLETOS, idCompra)).thenReturn(Optional.empty());
            when(carteraRepository.findByIdParaActualizar(cartera.getId())).thenReturn(Optional.of(cartera));
            when(movimientoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(carteraRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
            ArgumentCaptor<MovimientoCartera> captor = ArgumentCaptor.forClass(MovimientoCartera.class);

            // Act
            boolean aplicado = servicio.reembolsar(TipoReferencia.BOLETOS, idCompra, pago.getId(),
                    EventoFixture.FECHA_GUATEMALA, EventoFixture.MOTIVO);

            // Assert
            verify(movimientoRepository).save(captor.capture());
            MovimientoCartera reembolso = captor.getValue();
            assertAll(
                    () -> assertTrue(aplicado),
                    () -> assertEquals(TipoMovimiento.REEMBOLSO, reembolso.getTipo()),
                    () -> assertEquals(new BigDecimal("170.00"), reembolso.getMonto()),
                    () -> assertEquals(new BigDecimal("200.00"), reembolso.getSaldoPosterior()),
                    () -> assertEquals(TipoReferencia.BOLETOS, reembolso.getTipoReferencia()),
                    () -> assertEquals(idCompra, reembolso.getIdReferencia()),
                    () -> assertEquals(EventoFixture.FECHA_GUATEMALA, reembolso.getFechaMovimiento()),
                    () -> assertNull(reembolso.getClaveIdempotencia()),
                    () -> assertEquals(new BigDecimal("200.00"), cartera.getSaldo())
            );
            verify(eventos).carteraReembolsada(cartera, reembolso, EventoFixture.MOTIVO);
        }

        @Test
        void reembolsar_cuandoNoTraeIdPago_buscaPorReferencia() {
            // Arrange
            when(movimientoRepository.findByTipoAndTipoReferenciaAndIdReferencia(
                    TipoMovimiento.PAGO, TipoReferencia.BOLETOS, idCompra)).thenReturn(Optional.of(pago));
            when(movimientoRepository.findByTipoAndTipoReferenciaAndIdReferencia(
                    TipoMovimiento.REEMBOLSO, TipoReferencia.BOLETOS, idCompra)).thenReturn(Optional.empty());
            when(carteraRepository.findByIdParaActualizar(cartera.getId())).thenReturn(Optional.of(cartera));
            when(movimientoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(carteraRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

            // Act
            boolean aplicado = servicio.reembolsar(TipoReferencia.BOLETOS, idCompra, null,
                    EventoFixture.FECHA_GUATEMALA, EventoFixture.MOTIVO);

            // Assert
            assertTrue(aplicado);
            verify(movimientoRepository, never()).findByIdAndTipo(any(), any());
        }

        @Test
        void reembolsar_cuandoElIdPagoNoExiste_buscaPorReferencia() {
            // Arrange
            UUID idPagoDesconocido = UUID.randomUUID();
            when(movimientoRepository.findByIdAndTipo(idPagoDesconocido, TipoMovimiento.PAGO)).thenReturn(Optional.empty());
            when(movimientoRepository.findByTipoAndTipoReferenciaAndIdReferencia(
                    TipoMovimiento.PAGO, TipoReferencia.BOLETOS, idCompra)).thenReturn(Optional.empty());

            // Act
            boolean aplicado = servicio.reembolsar(TipoReferencia.BOLETOS, idCompra, idPagoDesconocido,
                    EventoFixture.FECHA_GUATEMALA, EventoFixture.MOTIVO);

            // Assert
            assertFalse(aplicado);
            verifyNoInteractions(carteraRepository, eventos);
        }

        @Test
        void reembolsar_cuandoYaSeReembolso_noDuplica() {
            // Arrange
            when(movimientoRepository.findByIdAndTipo(pago.getId(), TipoMovimiento.PAGO)).thenReturn(Optional.of(pago));
            when(movimientoRepository.findByTipoAndTipoReferenciaAndIdReferencia(
                    TipoMovimiento.REEMBOLSO, TipoReferencia.BOLETOS, idCompra))
                    .thenReturn(Optional.of(CarteraFixture.reembolso(cartera, TipoReferencia.BOLETOS, idCompra, "170.00")));
            when(carteraRepository.findByIdParaActualizar(cartera.getId())).thenReturn(Optional.of(cartera));

            // Act
            boolean aplicado = servicio.reembolsar(TipoReferencia.BOLETOS, idCompra, pago.getId(),
                    EventoFixture.FECHA_GUATEMALA, EventoFixture.MOTIVO);

            // Assert
            assertFalse(aplicado);
            verify(movimientoRepository, never()).save(any());
            verifyNoInteractions(eventos);
        }

        @Test
        void reembolsar_cuandoElPagoEsDeOtraReferencia_lanzaEventoNoRecuperable() {
            // Arrange
            when(movimientoRepository.findByIdAndTipo(pago.getId(), TipoMovimiento.PAGO)).thenReturn(Optional.of(pago));

            // Act & Assert
            assertAll(
                    () -> assertThrows(EventoNoRecuperableException.class, () -> servicio.reembolsar(
                            TipoReferencia.BOLETOS, UUID.randomUUID(), pago.getId(), EventoFixture.FECHA_GUATEMALA, null)),
                    () -> assertThrows(EventoNoRecuperableException.class, () -> servicio.reembolsar(
                            TipoReferencia.ALQUILER, idCompra, pago.getId(), EventoFixture.FECHA_GUATEMALA, null))
            );
            verifyNoInteractions(carteraRepository, eventos);
        }

        @Test
        void reembolsar_cuandoNoTraeMotivo_usaViajeEliminado() {
            // Arrange
            when(movimientoRepository.findByIdAndTipo(pago.getId(), TipoMovimiento.PAGO)).thenReturn(Optional.of(pago));
            when(movimientoRepository.findByTipoAndTipoReferenciaAndIdReferencia(
                    TipoMovimiento.REEMBOLSO, TipoReferencia.BOLETOS, idCompra)).thenReturn(Optional.empty());
            when(carteraRepository.findByIdParaActualizar(cartera.getId())).thenReturn(Optional.of(cartera));
            when(movimientoRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(carteraRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

            // Act
            servicio.reembolsar(TipoReferencia.BOLETOS, idCompra, pago.getId(), EventoFixture.FECHA_GUATEMALA, " ");

            // Assert
            verify(eventos).carteraReembolsada(eq(cartera), any(), eq(ReembolsoServiceImpl.MOTIVO_POR_DEFECTO));
        }

        @Test
        void reembolsar_cuandoLaCarteraNoExiste_lanzaEventoNoRecuperable() {
            // Arrange
            when(movimientoRepository.findByIdAndTipo(pago.getId(), TipoMovimiento.PAGO)).thenReturn(Optional.of(pago));
            when(carteraRepository.findByIdParaActualizar(cartera.getId())).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(EventoNoRecuperableException.class, () -> servicio.reembolsar(TipoReferencia.BOLETOS,
                    idCompra, pago.getId(), EventoFixture.FECHA_GUATEMALA, EventoFixture.MOTIVO));
        }
    }
}
