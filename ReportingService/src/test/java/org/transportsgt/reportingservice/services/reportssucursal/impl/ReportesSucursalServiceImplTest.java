package org.transportsgt.reportingservice.services.reportssucursal.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.eventos.payload.EstadoOperativo;
import org.transportsgt.reportingservice.config.security.UsuarioAutenticado;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.GrupoReporte;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaGastoTaller;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaBus;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaChofer;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaDepreciacionBus;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaIngresoAlquiler;
import org.transportsgt.reportingservice.dto.response.reportssucursal.FilaIngresoBoleto;
import org.transportsgt.reportingservice.exception.SinPermisoSucursalException;
import org.transportsgt.reportingservice.fixture.RepBusFixture;
import org.transportsgt.reportingservice.fixture.RepChoferFixture;
import org.transportsgt.reportingservice.fixture.RepGastoFixture;
import org.transportsgt.reportingservice.fixture.RepIngresoFixture;
import org.transportsgt.reportingservice.fixture.RepRutaFixture;
import org.transportsgt.reportingservice.fixture.RepSucursalFixture;
import org.transportsgt.reportingservice.fixture.RepTarifaDepreciacionFixture;
import org.transportsgt.reportingservice.fixture.RepViajeFixture;
import org.transportsgt.reportingservice.models.RepSucursal;
import org.transportsgt.reportingservice.repositories.RepSucursalRepository;
import org.transportsgt.reportingservice.repositories.RepTarifaDepreciacionRepository;
import org.transportsgt.reportingservice.repositories.reportsadmin.ReporteGastosTallerRepository;
import org.transportsgt.reportingservice.repositories.reportssucursal.ReporteBusesRepository;
import org.transportsgt.reportingservice.repositories.reportssucursal.ReporteChoferesRepository;
import org.transportsgt.reportingservice.repositories.reportssucursal.ReporteDepreciacionBusesRepository;
import org.transportsgt.reportingservice.repositories.reportssucursal.ReporteIngresosAlquileresRepository;
import org.transportsgt.reportingservice.repositories.reportssucursal.ReporteIngresosBoletosRepository;
import org.transportsgt.reportingservice.services.comun.AlcanceSucursal;
import org.transportsgt.reportingservice.support.Soporte;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportesSucursalServiceImplTest {

    @Mock
    private ReporteBusesRepository busesRepository;
    @Mock
    private ReporteChoferesRepository choferesRepository;
    @Mock
    private ReporteIngresosBoletosRepository boletosRepository;
    @Mock
    private ReporteIngresosAlquileresRepository alquileresRepository;
    @Mock
    private ReporteDepreciacionBusesRepository depreciacionRepository;
    @Mock
    private RepTarifaDepreciacionRepository tarifaRepository;
    @Mock
    private ReporteGastosTallerRepository tallerRepository;
    @Mock
    private RepSucursalRepository sucursalRepository;
    @Mock
    private AlcanceSucursal alcance;

    private UsuarioAutenticado adminXela;
    private RepSucursal xela;
    private FiltroFechas todo;
    private ReporteBusesServiceImpl buses;
    private ReporteChoferesServiceImpl choferes;
    private ReporteIngresosBoletosServiceImpl boletos;
    private ReporteIngresosAlquileresServiceImpl alquileres;
    private ReporteDepreciacionBusesServiceImpl depreciacion;
    private ReporteGastosTallerBusServiceImpl tallerBus;

    @BeforeEach
    void setUp() {
        adminXela = Soporte.adminSucursal(RepSucursalFixture.QUETZALTENANGO);
        xela = RepSucursalFixture.quetzaltenango();
        todo = new FiltroFechas(null, null);
        buses = new ReporteBusesServiceImpl(busesRepository, alcance, Soporte.armador(), Soporte.fechas());
        choferes = new ReporteChoferesServiceImpl(choferesRepository, alcance, Soporte.armador(), Soporte.fechas());
        boletos = new ReporteIngresosBoletosServiceImpl(boletosRepository, alcance, Soporte.armador(), Soporte.textos(),
                sucursalRepository, Soporte.fechas());
        alquileres = new ReporteIngresosAlquileresServiceImpl(alquileresRepository, alcance, Soporte.armador(),
                Soporte.fechas());
        depreciacion = new ReporteDepreciacionBusesServiceImpl(depreciacionRepository, tarifaRepository, alcance,
                Soporte.armador(), Soporte.dinero(), Soporte.fechas());
        tallerBus = new ReporteGastosTallerBusServiceImpl(tallerRepository, alcance, Soporte.armador(),
                Soporte.filasTaller());
    }

    @Nested
    class Buses {

        @Test
        void generar_usaLaSucursalDelTokenYElRelojParaElProximoViaje() {
            // Arrange
            when(alcance.sucursalDelReporte(adminXela, null)).thenReturn(xela);
            when(busesRepository.consultar(eq(RepSucursalFixture.QUETZALTENANGO), eq("EN_TALLER"), any(), isNull(),
                    isNull())).thenReturn(List.of(RepBusFixture.consulta("P900ABC", "José", 3L, true),
                    RepBusFixture.consulta("P901ABC", null, null, null)));

            // Act
            ReporteTabla<FilaBus> reporte = buses.generar(adminXela, null, todo, EstadoOperativo.EN_TALLER);

            // Assert
            List<FilaBus> filas = reporte.grupos().getFirst().filas();
            assertAll(
                    () -> assertEquals("José", filas.getFirst().chofer()),
                    () -> assertNull(filas.get(1).chofer()),
                    () -> assertEquals(0L, filas.get(1).viajesRealizados()),
                    () -> assertEquals(2L, reporte.totales().get("buses")),
                    () -> assertEquals(1L, reporte.totales().get("activos")),
                    () -> assertEquals(3L, reporte.totales().get("viajesRealizados")),
                    () -> assertEquals("EN_TALLER", reporte.filtros().otros().get("estado")),
                    () -> assertEquals("Quetzaltenango", reporte.filtros().otros().get("sucursal"))
            );
        }

        @Test
        void generar_cuandoPideOtraSucursal_propagaEl403SinConsultar() {
            // Arrange
            when(alcance.sucursalDelReporte(adminXela, RepSucursalFixture.GUATEMALA))
                    .thenThrow(new SinPermisoSucursalException());

            // Act & Assert
            assertThrows(SinPermisoSucursalException.class,
                    () -> buses.generar(adminXela, RepSucursalFixture.GUATEMALA, todo, null));
            verifyNoInteractions(busesRepository);
        }

        @Test
        void generar_sinEstado_noLoInforma() {
            // Arrange
            when(alcance.sucursalDelReporte(adminXela, null)).thenReturn(xela);
            when(busesRepository.consultar(eq(RepSucursalFixture.QUETZALTENANGO), isNull(), any(), isNull(), isNull()))
                    .thenReturn(List.of());

            // Act & Assert
            assertNull(buses.generar(adminXela, null, todo, null).filtros().otros().get("estado"));
        }
    }

    @Nested
    class Choferes {

        @Test
        void generar_marcaLicenciasVencidasYPorVencerConElRelojDeGuatemala() {
            // Arrange
            when(alcance.sucursalDelReporte(adminXela, null)).thenReturn(xela);
            when(choferesRepository.consultar(RepSucursalFixture.QUETZALTENANGO, null, null)).thenReturn(List.of(
                    RepChoferFixture.consulta("Vencido", Soporte.HOY.minusDays(1), true, 2L),
                    RepChoferFixture.consulta("Hoy", Soporte.HOY, true, 0L),
                    RepChoferFixture.consulta("Treinta", Soporte.HOY.plusDays(30), false, null),
                    RepChoferFixture.consulta("Vigente", Soporte.HOY.plusDays(31), null, 1L),
                    RepChoferFixture.consulta("SinFecha", null, true, 0L)));

            // Act
            ReporteTabla<FilaChofer> reporte = choferes.generar(adminXela, null, todo);

            // Assert
            List<String> estados = reporte.grupos().getFirst().filas().stream().map(FilaChofer::estadoLicencia).toList();
            assertAll(
                    () -> assertEquals(List.of("VENCIDA", "POR_VENCER", "POR_VENCER", "VIGENTE", "SIN_FECHA"), estados),
                    () -> assertEquals(1L, reporte.totales().get("licenciasVencidas")),
                    () -> assertEquals(2L, reporte.totales().get("licenciasPorVencer")),
                    () -> assertEquals(3L, reporte.totales().get("activos")),
                    () -> assertEquals(3L, reporte.totales().get("viajesRealizados"))
            );
        }
    }

    @Nested
    class IngresosBoletos {

        @Test
        void generar_conRutaYBus_informaTrayectoYPlacaYTotaliza() {
            // Arrange
            UUID idRuta = UUID.randomUUID();
            UUID idBus = UUID.randomUUID();
            when(alcance.sucursalDelReporte(adminXela, null)).thenReturn(xela);
            when(alcance.rutaDeSucursal(idRuta, RepSucursalFixture.QUETZALTENANGO)).thenReturn(Optional.of(
                    RepRutaFixture.ruta(idRuta, RepSucursalFixture.QUETZALTENANGO, RepSucursalFixture.GUATEMALA)));
            when(alcance.busDeSucursal(idBus, RepSucursalFixture.QUETZALTENANGO)).thenReturn(Optional.of(
                    RepBusFixture.bus(idBus, RepSucursalFixture.QUETZALTENANGO, "P900ABC")));
            when(sucursalRepository.findById(RepSucursalFixture.QUETZALTENANGO)).thenReturn(Optional.of(xela));
            when(sucursalRepository.findById(RepSucursalFixture.GUATEMALA)).thenReturn(Optional.empty());
            when(boletosRepository.consultar(RepSucursalFixture.QUETZALTENANGO, null, null, idRuta, idBus))
                    .thenReturn(List.of(RepViajeFixture.ingresoBoleto(5L, "425.00"),
                            RepViajeFixture.ingresoBoleto(null, "0.00")));

            // Act
            ReporteTabla<FilaIngresoBoleto> reporte = boletos.generar(adminXela, null, todo, idRuta, idBus);

            // Assert
            FilaIngresoBoleto primera = reporte.grupos().getFirst().filas().getFirst();
            assertAll(
                    () -> assertEquals("Quetzaltenango → —", reporte.filtros().otros().get("ruta")),
                    () -> assertEquals("P900ABC", reporte.filtros().otros().get("bus")),
                    () -> assertEquals("Quetzaltenango → Ciudad de Guatemala", primera.ruta()),
                    () -> assertEquals("2026-10-25T06:00-06:00", primera.fechaHoraSalida().toString()),
                    () -> assertEquals(5L, reporte.totales().get("boletosVendidos")),
                    () -> assertEquals("425.00", reporte.totales().get("ingreso")),
                    () -> assertEquals(2L, reporte.totales().get("viajes"))
            );
        }
    }

    @Nested
    class IngresosAlquileres {

        @Test
        void generar_convierteFechasAGuatemalaYTotaliza() {
            // Arrange
            when(alcance.sucursalDelReporte(adminXela, null)).thenReturn(xela);
            when(alquileresRepository.consultar(RepSucursalFixture.QUETZALTENANGO, null, null)).thenReturn(List.of(
                    RepIngresoFixture.ingresoAlquiler("3800.00", Instant.parse("2026-10-27T00:00:00Z")),
                    RepIngresoFixture.ingresoAlquiler("700.00", null)));

            // Act
            ReporteTabla<FilaIngresoAlquiler> reporte = alquileres.generar(adminXela, null, todo);

            // Assert
            List<FilaIngresoAlquiler> filas = reporte.grupos().getFirst().filas();
            assertAll(
                    () -> assertEquals("2026-10-26T18:00-06:00", filas.getFirst().fechaRetorno().toString()),
                    () -> assertNull(filas.get(1).fechaRetorno()),
                    () -> assertEquals("4500.00", reporte.totales().get("precioTotal")),
                    () -> assertEquals(2L, reporte.totales().get("alquileres"))
            );
        }
    }

    @Nested
    class Depreciacion {

        @Test
        void generar_usaLaTarifaVigenteHoyYCalculaPromedios() {
            // Arrange
            when(alcance.sucursalDelReporte(adminXela, null)).thenReturn(xela);
            when(tarifaRepository.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(Soporte.HOY))
                    .thenReturn(Optional.of(RepTarifaDepreciacionFixture.tarifa("2.5000", LocalDate.parse("2026-10-01"))));
            when(depreciacionRepository.consultar(RepSucursalFixture.QUETZALTENANGO, null, null)).thenReturn(List.of(
                    RepBusFixture.depreciacion("P900ABC", "205.5", "411.00"),
                    RepBusFixture.depreciacion("P901ABC", "0", "0")));

            // Act
            ReporteTabla<FilaDepreciacionBus> reporte = depreciacion.generar(adminXela, null, todo);

            // Assert
            List<FilaDepreciacionBus> filas = reporte.grupos().getFirst().filas();
            assertAll(
                    () -> assertEquals(new BigDecimal("2.50"), filas.getFirst().montoPorKmActual()),
                    () -> assertEquals(new BigDecimal("2.00"), filas.getFirst().promedioPorKm()),
                    () -> assertEquals(new BigDecimal("0.00"), filas.get(1).promedioPorKm()),
                    () -> assertEquals("205.5", reporte.totales().get("kmRecorridos")),
                    () -> assertEquals("411.00", reporte.totales().get("depreciacionAcumulada")),
                    () -> assertEquals("2.00", reporte.totales().get("promedioPorKm")),
                    () -> assertEquals("2.50", reporte.totales().get("montoPorKmActual"))
            );
        }

        @Test
        void generar_sinTarifaConfigurada_dejaLaTarifaVacia() {
            // Arrange
            when(alcance.sucursalDelReporte(adminXela, null)).thenReturn(xela);
            when(tarifaRepository.findFirstByVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc(Soporte.HOY))
                    .thenReturn(Optional.empty());
            when(depreciacionRepository.consultar(RepSucursalFixture.QUETZALTENANGO, null, null)).thenReturn(List.of(
                    new RepBusFixture.Depreciacion(UUID.randomUUID(), "P1", null, BigDecimal.ZERO)));

            // Act
            ReporteTabla<FilaDepreciacionBus> reporte = depreciacion.generar(adminXela, null, todo);

            // Assert
            assertAll(
                    () -> assertNull(reporte.grupos().getFirst().filas().getFirst().montoPorKmActual()),
                    () -> assertTrue(!reporte.totales().containsKey("montoPorKmActual")),
                    () -> assertEquals("0", reporte.totales().get("kmRecorridos"))
            );
        }
    }

    @Nested
    class TallerPorBus {

        @Test
        void generar_agrupaPorBusYCategoriaConBusesDeMayorAMenorGasto() {
            // Arrange
            UUID bus1 = UUID.randomUUID();
            UUID bus2 = UUID.randomUUID();
            when(alcance.sucursalDelReporte(adminXela, null)).thenReturn(xela);
            when(alcance.busDeSucursal(null, RepSucursalFixture.QUETZALTENANGO)).thenReturn(Optional.empty());
            when(tallerRepository.consultar(null, null, RepSucursalFixture.QUETZALTENANGO, null)).thenReturn(List.of(
                    RepGastoFixture.taller("Aceite", bus1, "P900ABC", "150.00"),
                    RepGastoFixture.taller("Motor", bus2, "P901ABC", "2000.00"),
                    RepGastoFixture.taller("Frenos", bus1, "P900ABC", "300.00"),
                    RepGastoFixture.taller("Aceite", bus1, "P900ABC", "100.00")));

            // Act
            ReporteTabla<FilaGastoTaller> reporte = tallerBus.generar(adminXela, null, todo, null);

            // Assert
            GrupoReporte<FilaGastoTaller> primero = reporte.grupos().getFirst();
            GrupoReporte<FilaGastoTaller> segundo = reporte.grupos().get(1);
            assertAll(
                    () -> assertEquals("P901ABC", primero.titulo()),
                    () -> assertEquals("P900ABC", segundo.titulo()),
                    () -> assertEquals(List.of("Aceite", "Frenos"),
                            segundo.subgrupos().stream().map(GrupoReporte::titulo).toList()),
                    () -> assertEquals("250.00", segundo.subgrupos().getFirst().subtotales().get("monto")),
                    () -> assertEquals("550.00", segundo.subtotales().get("monto")),
                    () -> assertEquals("2550.00", reporte.totales().get("monto"))
            );
        }

        @Test
        void generar_conBus_loInformaEnLosFiltros() {
            // Arrange
            UUID idBus = UUID.randomUUID();
            when(alcance.sucursalDelReporte(adminXela, null)).thenReturn(xela);
            when(alcance.busDeSucursal(idBus, RepSucursalFixture.QUETZALTENANGO)).thenReturn(Optional.of(
                    RepBusFixture.bus(idBus, RepSucursalFixture.QUETZALTENANGO, "P900ABC")));
            when(tallerRepository.consultar(null, null, RepSucursalFixture.QUETZALTENANGO, idBus)).thenReturn(List.of());

            // Act & Assert
            assertEquals("P900ABC", tallerBus.generar(adminXela, null, todo, idBus).filtros().otros().get("bus"));
        }
    }
}
