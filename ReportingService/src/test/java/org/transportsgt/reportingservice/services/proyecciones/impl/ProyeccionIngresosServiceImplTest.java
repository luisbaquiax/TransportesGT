package org.transportsgt.reportingservice.services.proyecciones.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.mensajeria.excepciones.EventoNoRecuperableException;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.reportingservice.exception.ProyeccionNoDisponibleException;
import org.transportsgt.reportingservice.fixture.EventoFixture;
import org.transportsgt.reportingservice.fixture.RepSucursalFixture;
import org.transportsgt.reportingservice.models.TipoIngreso;
import org.transportsgt.reportingservice.repositories.RepIngresoRepository;
import org.transportsgt.reportingservice.utils.Fechas;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProyeccionIngresosServiceImplTest {

    @Mock
    private RepIngresoRepository ingresoRepository;

    private ProyeccionIngresosServiceImpl servicio;
    private UUID idCompra;
    private UUID idViaje;
    private UUID idRuta;
    private UUID idAlquiler;

    @BeforeEach
    void setUp() {
        servicio = new ProyeccionIngresosServiceImpl(new CamposObligatorios(), ingresoRepository,
                new Fechas(Clock.systemUTC()));
        idCompra = UUID.randomUUID();
        idViaje = UUID.randomUUID();
        idRuta = UUID.randomUUID();
        idAlquiler = UUID.randomUUID();
    }

    @Nested
    class RegistrarVenta {

        @Test
        void registrarVenta_cuandoLlega_insertaConCantidadDeAsientosYFechaDePago() {
            // Arrange
            when(ingresoRepository.insertar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any())).thenReturn(1);

            // Act
            servicio.registrarVenta(EventoFixture.sobre(TipoEvento.VENTA_BOLETOS_REGISTRADA, EventoFixture.venta(
                    idCompra, RepSucursalFixture.QUETZALTENANGO, idViaje, idRuta, "170.00", 2)));

            // Assert
            verify(ingresoRepository).insertar("VENTA_BOLETOS", idCompra, RepSucursalFixture.QUETZALTENANGO,
                    EventoFixture.FECHA_PAGO, new BigDecimal("170.00"), idViaje, idRuta, 2, "EN_LINEA", "1234567K",
                    "Ana López", null, null, null, null);
        }

        @Test
        void registrarVenta_cuandoDuplicado_noFalla() {
            // Arrange
            when(ingresoRepository.insertar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any())).thenReturn(0);

            // Act
            servicio.registrarVenta(EventoFixture.sobre(TipoEvento.VENTA_BOLETOS_REGISTRADA, EventoFixture.venta(
                    idCompra, RepSucursalFixture.QUETZALTENANGO, idViaje, idRuta, "85.00", 1)));

            // Assert
            verify(ingresoRepository).insertar(eq("VENTA_BOLETOS"), eq(idCompra), any(), any(), any(), any(), any(),
                    eq(1), any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        void registrarVenta_cuandoNoTraeIdCompra_esNoRecuperable() {
            // Act & Assert
            assertThrows(EventoNoRecuperableException.class, () -> servicio.registrarVenta(EventoFixture.sobre(
                    TipoEvento.VENTA_BOLETOS_REGISTRADA, EventoFixture.venta(null, RepSucursalFixture.QUETZALTENANGO,
                            idViaje, idRuta, "85.00", 1))));
        }
    }

    @Nested
    class AnularVenta {

        @Test
        void anularVenta_cuandoLaVentaExiste_laAnulaConLaHoraDelEvento() {
            // Arrange
            when(ingresoRepository.existsByTipoIngresoAndIdReferencia(TipoIngreso.VENTA_BOLETOS, idCompra))
                    .thenReturn(true);
            when(ingresoRepository.anular(any(), any(), any())).thenReturn(1);

            // Act
            servicio.anularVenta(EventoFixture.sobre(TipoEvento.BOLETOS_ANULADOS, EventoFixture.boletosAnulados(idCompra)));

            // Assert
            verify(ingresoRepository).anular(TipoIngreso.VENTA_BOLETOS, idCompra,
                    OffsetDateTime.parse("2026-10-21T23:30-06:00"));
        }

        @Test
        void anularVenta_cuandoYaEstabaAnulada_noFalla() {
            // Arrange
            when(ingresoRepository.existsByTipoIngresoAndIdReferencia(TipoIngreso.VENTA_BOLETOS, idCompra))
                    .thenReturn(true);
            when(ingresoRepository.anular(any(), any(), any())).thenReturn(0);

            // Act
            servicio.anularVenta(EventoFixture.sobre(TipoEvento.BOLETOS_ANULADOS, EventoFixture.boletosAnulados(idCompra)));

            // Assert
            verify(ingresoRepository).anular(eq(TipoIngreso.VENTA_BOLETOS), eq(idCompra), any());
        }

        @Test
        void anularVenta_cuandoLaVentaAunNoLlega_esTransitorioYNoAnula() {
            // Arrange
            when(ingresoRepository.existsByTipoIngresoAndIdReferencia(TipoIngreso.VENTA_BOLETOS, idCompra))
                    .thenReturn(false);

            // Act & Assert
            assertThrows(ProyeccionNoDisponibleException.class, () -> servicio.anularVenta(
                    EventoFixture.sobre(TipoEvento.BOLETOS_ANULADOS, EventoFixture.boletosAnulados(idCompra))));
            verify(ingresoRepository, never()).anular(any(), any(), any());
        }
    }

    @Nested
    class Alquileres {

        @Test
        void registrarAlquiler_cuandoTienePago_esEnLineaConPrecioTotal() {
            // Arrange
            when(ingresoRepository.insertar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any())).thenReturn(1);

            // Act
            servicio.registrarAlquiler(EventoFixture.sobre(TipoEvento.ALQUILER_CONFIRMADO, EventoFixture.alquiler(
                    idAlquiler, RepSucursalFixture.HUEHUETENANGO, UUID.randomUUID(), "3800.00")));

            // Assert
            verify(ingresoRepository).insertar(eq("ALQUILER"), eq(idAlquiler), eq(RepSucursalFixture.HUEHUETENANGO),
                    eq(EventoFixture.FECHA_PAGO), eq(new BigDecimal("3800.00")), isNull(), isNull(), isNull(),
                    eq("EN_LINEA"), eq("CF"), eq("Empresa Ñandú"), eq("Quetzaltenango"), eq("San Marcos"),
                    eq(EventoFixture.SALIDA), any());
        }

        @Test
        void registrarAlquiler_cuandoNoTienePago_esMostrador() {
            // Arrange
            when(ingresoRepository.insertar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any())).thenReturn(0);

            // Act
            servicio.registrarAlquiler(EventoFixture.sobre(TipoEvento.ALQUILER_CONFIRMADO, EventoFixture.alquiler(
                    idAlquiler, RepSucursalFixture.HUEHUETENANGO, null, "700.00")));

            // Assert
            verify(ingresoRepository).insertar(any(), any(), any(), any(), any(), any(), any(), any(), eq("MOSTRADOR"),
                    any(), any(), any(), any(), any(), any());
        }

        @Test
        void cancelarAlquiler_cuandoExiste_usaCanceladoEnYSiFalta_ocurridoEn() {
            // Arrange
            Instant canceladoEn = Instant.parse("2026-10-22T15:15:00Z");
            when(ingresoRepository.existsByTipoIngresoAndIdReferencia(TipoIngreso.ALQUILER, idAlquiler))
                    .thenReturn(true);
            when(ingresoRepository.anular(any(), any(), any())).thenReturn(1);

            // Act
            servicio.cancelarAlquiler(EventoFixture.sobre(TipoEvento.ALQUILER_CANCELADO,
                    EventoFixture.alquilerCancelado(idAlquiler, canceladoEn)));
            servicio.cancelarAlquiler(EventoFixture.sobre(TipoEvento.ALQUILER_CANCELADO,
                    EventoFixture.alquilerCancelado(idAlquiler, null)));

            // Assert
            verify(ingresoRepository).anular(TipoIngreso.ALQUILER, idAlquiler,
                    canceladoEn.atOffset(ZoneOffset.ofHours(-6)));
            verify(ingresoRepository).anular(TipoIngreso.ALQUILER, idAlquiler,
                    EventoFixture.OCURRIDO_EN.atOffset(ZoneOffset.ofHours(-6)));
        }

        @Test
        void cancelarAlquiler_cuandoAunNoLlega_esTransitorio() {
            // Arrange
            when(ingresoRepository.existsByTipoIngresoAndIdReferencia(TipoIngreso.ALQUILER, idAlquiler))
                    .thenReturn(false);

            // Act & Assert
            assertThrows(ProyeccionNoDisponibleException.class, () -> servicio.cancelarAlquiler(EventoFixture.sobre(
                    TipoEvento.ALQUILER_CANCELADO, EventoFixture.alquilerCancelado(idAlquiler, null))));
        }
    }
}
