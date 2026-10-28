package org.transportsgt.reportingservice.services.proyecciones.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.mensajeria.excepciones.EventoNoRecuperableException;
import org.transportsgt.eventos.contrato.TipoEvento;
import org.transportsgt.eventos.payload.costos.LineaTaller;
import org.transportsgt.eventos.payload.costos.TipoLinea;
import org.transportsgt.reportingservice.fixture.EventoFixture;
import org.transportsgt.reportingservice.fixture.RepSucursalFixture;
import org.transportsgt.reportingservice.fixture.RepViajeFixture;
import org.transportsgt.reportingservice.repositories.RepGastoRepository;
import org.transportsgt.reportingservice.repositories.RepTarifaDepreciacionRepository;
import org.transportsgt.reportingservice.repositories.RepViajeRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProyeccionGastosServiceImplTest {

    @Mock
    private RepGastoRepository gastoRepository;
    @Mock
    private RepTarifaDepreciacionRepository tarifaRepository;
    @Mock
    private RepViajeRepository viajeRepository;

    private ProyeccionGastosServiceImpl servicio;
    private UUID idSucursal;
    private UUID idBus;

    @BeforeEach
    void setUp() {
        servicio = new ProyeccionGastosServiceImpl(new CamposObligatorios(), gastoRepository, tarifaRepository,
                viajeRepository);
        idSucursal = RepSucursalFixture.QUETZALTENANGO;
        idBus = UUID.randomUUID();
    }

    @Nested
    class Combustible {

        @Test
        void registrarCombustible_cuandoLlega_insertaGastoCombustible() {
            // Arrange
            UUID idGasto = UUID.randomUUID();
            when(gastoRepository.insertar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any())).thenReturn(1);

            // Act
            servicio.registrarCombustible(EventoFixture.sobre(TipoEvento.GASTO_COMBUSTIBLE_REGISTRADO,
                    EventoFixture.combustible(idGasto, idSucursal, idBus, "420.00")));

            // Assert
            verify(gastoRepository).insertar(eq("COMBUSTIBLE"), eq(idGasto), eq(idSucursal), eq(idBus), any(),
                    eq(EventoFixture.FECHA_PAGO), eq(new BigDecimal("420.00")), isNull(), isNull(), isNull(), isNull(),
                    isNull(), isNull());
        }

        @Test
        void registrarCombustible_cuandoNoTraeId_esNoRecuperable() {
            assertThrows(EventoNoRecuperableException.class, () -> servicio.registrarCombustible(EventoFixture.sobre(
                    TipoEvento.GASTO_COMBUSTIBLE_REGISTRADO, EventoFixture.combustible(null, idSucursal, idBus, "1"))));
        }
    }

    @Nested
    class Depreciacion {

        @Test
        void registrarDepreciacion_cuandoDuplicada_insertaConKmYTarifaSinFallar() {
            // Arrange
            UUID id = UUID.randomUUID();
            when(gastoRepository.insertar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any())).thenReturn(0);

            // Act
            servicio.registrarDepreciacion(EventoFixture.sobre(TipoEvento.DEPRECIACION_CALCULADA,
                    EventoFixture.depreciacion(id, idSucursal, idBus, "205.5", "411.00")));

            // Assert
            verify(gastoRepository).insertar(eq("DEPRECIACION"), eq(id), eq(idSucursal), eq(idBus), any(), any(),
                    eq(new BigDecimal("411.00")), isNull(), isNull(), isNull(), isNull(), eq(new BigDecimal("205.5")),
                    eq(new BigDecimal("2.00")));
        }
    }

    @Nested
    class Taller {

        @Test
        void registrarTaller_cuandoTraeDosLineas_insertaUnaFilaPorLinea() {
            // Arrange
            UUID linea1 = UUID.randomUUID();
            UUID linea2 = UUID.randomUUID();
            List<LineaTaller> lineas = List.of(EventoFixture.linea(linea1, "Frenos", TipoLinea.REPUESTO, "850.00"),
                    EventoFixture.linea(linea2, "Frenos", null, "300.00"));
            when(gastoRepository.insertar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any())).thenReturn(1);

            // Act
            servicio.registrarTaller(EventoFixture.sobre(TipoEvento.GASTO_TALLER_REGISTRADO,
                    EventoFixture.taller(UUID.randomUUID(), idSucursal, idBus, lineas)));

            // Assert
            verify(gastoRepository, times(2)).insertar(eq("TALLER"), any(), eq(idSucursal), eq(idBus), isNull(),
                    eq(EventoFixture.FECHA_PAGO), any(), any(), eq("Frenos"), any(), any(), isNull(), isNull());
            verify(gastoRepository).insertar(eq("TALLER"), eq(linea1), any(), any(), any(), any(),
                    eq(new BigDecimal("850.00")), any(), any(), eq("REPUESTO"), eq("Detalle Frenos"), any(), any());
            verify(gastoRepository).insertar(eq("TALLER"), eq(linea2), any(), any(), any(), any(), any(), any(), any(),
                    isNull(), any(), any(), any());
        }

        @Test
        void registrarTaller_cuandoNoTraeLineas_esNoRecuperable() {
            assertThrows(EventoNoRecuperableException.class, () -> servicio.registrarTaller(EventoFixture.sobre(
                    TipoEvento.GASTO_TALLER_REGISTRADO,
                    new org.transportsgt.eventos.payload.costos.DatosGastoTaller(UUID.randomUUID(), idBus, idSucursal,
                            EventoFixture.FECHA_PAGO, BigDecimal.ONE, null))));
        }
    }

    @Nested
    class PagoChofer {

        @Test
        void registrarPagoChofer_cuandoElViajeEstaProyectado_tomaElBusDelViaje() {
            // Arrange
            UUID idPago = UUID.randomUUID();
            UUID idViaje = UUID.randomUUID();
            when(viajeRepository.findById(idViaje)).thenReturn(Optional.of(RepViajeFixture.viaje(idViaje, idBus)));
            when(gastoRepository.insertar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any())).thenReturn(1);

            // Act
            servicio.registrarPagoChofer(EventoFixture.sobre(TipoEvento.PAGO_CHOFER_REGISTRADO,
                    EventoFixture.pagoChofer(idPago, idSucursal, idViaje, "172.50")));

            // Assert
            verify(gastoRepository).insertar(eq("SALARIO_CHOFER"), eq(idPago), eq(idSucursal), eq(idBus), eq(idViaje),
                    eq(EventoFixture.FECHA_PAGO), eq(new BigDecimal("172.50")), isNull(), isNull(), isNull(), isNull(),
                    isNull(), isNull());
        }

        @Test
        void registrarPagoChofer_cuandoElViajeNoEstaProyectado_dejaElBusVacio() {
            // Arrange
            UUID idViaje = UUID.randomUUID();
            when(viajeRepository.findById(idViaje)).thenReturn(Optional.empty());
            when(gastoRepository.insertar(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any())).thenReturn(1);

            // Act
            servicio.registrarPagoChofer(EventoFixture.sobre(TipoEvento.PAGO_CHOFER_REGISTRADO,
                    EventoFixture.pagoChofer(UUID.randomUUID(), idSucursal, idViaje, "150.00")));

            // Assert
            verify(gastoRepository).insertar(eq("SALARIO_CHOFER"), any(), any(), isNull(), eq(idViaje), any(), any(),
                    any(), any(), any(), any(), any(), any());
        }
    }

    @Nested
    class CambiarTarifa {

        @Test
        void cambiarTarifa_cuandoLlega_haceUpsertPorVigenteDesde() {
            // Arrange
            LocalDate vigenteDesde = LocalDate.parse("2026-11-01");

            // Act
            servicio.cambiarTarifa(EventoFixture.sobre(TipoEvento.TARIFA_DEPRECIACION_CAMBIADA,
                    EventoFixture.tarifa("2.50", vigenteDesde)));

            // Assert
            verify(tarifaRepository).upsert(vigenteDesde, new BigDecimal("2.50"));
        }

        @Test
        void cambiarTarifa_cuandoNoTraeVigenteDesde_esNoRecuperable() {
            assertThrows(EventoNoRecuperableException.class, () -> servicio.cambiarTarifa(EventoFixture.sobre(
                    TipoEvento.TARIFA_DEPRECIACION_CAMBIADA, EventoFixture.tarifa("2.50", null))));
        }
    }
}
