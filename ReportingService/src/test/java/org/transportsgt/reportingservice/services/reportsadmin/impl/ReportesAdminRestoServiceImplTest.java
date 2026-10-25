package org.transportsgt.reportingservice.services.reportsadmin.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.GrupoReporte;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaGanancia;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaGastoTaller;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaMapaRuta;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaRutaDemandada;
import org.transportsgt.reportingservice.exception.SucursalRequeridaException;
import org.transportsgt.reportingservice.fixture.RepBusFixture;
import org.transportsgt.reportingservice.fixture.RepGastoFixture;
import org.transportsgt.reportingservice.fixture.RepRutaFixture;
import org.transportsgt.reportingservice.fixture.RepSucursalFixture;
import org.transportsgt.reportingservice.repositories.reportsadmin.ReporteGananciasRepository;
import org.transportsgt.reportingservice.repositories.reportsadmin.ReporteGastosTallerRepository;
import org.transportsgt.reportingservice.repositories.reportsadmin.ReporteMapaRutasRepository;
import org.transportsgt.reportingservice.repositories.reportsadmin.ReporteRutasDemandadasRepository;
import org.transportsgt.reportingservice.services.comun.AlcanceSucursal;
import org.transportsgt.reportingservice.support.Soporte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportesAdminRestoServiceImplTest {

    @Mock
    private ReporteGananciasRepository gananciasRepository;
    @Mock
    private ReporteRutasDemandadasRepository rutasRepository;
    @Mock
    private ReporteGastosTallerRepository tallerRepository;
    @Mock
    private ReporteMapaRutasRepository mapaRepository;
    @Mock
    private AlcanceSucursal alcance;

    private ReporteGananciasServiceImpl ganancias;
    private ReporteRutasDemandadasServiceImpl rutas;
    private ReporteGastosTallerCategoriaServiceImpl tallerCategoria;
    private ReporteMapaRutasServiceImpl mapa;
    private FiltroFechas todo;

    @BeforeEach
    void setUp() {
        ganancias = new ReporteGananciasServiceImpl(gananciasRepository, alcance, Soporte.armador(), Soporte.dinero());
        rutas = new ReporteRutasDemandadasServiceImpl(rutasRepository, Soporte.armador(), Soporte.textos(),
                Soporte.dinero());
        tallerCategoria = new ReporteGastosTallerCategoriaServiceImpl(tallerRepository, alcance, Soporte.armador(),
                Soporte.filasTaller());
        mapa = new ReporteMapaRutasServiceImpl(mapaRepository, alcance, Soporte.armador(), Soporte.textos());
        todo = new FiltroFechas(null, null);
    }

    @Nested
    class Ganancias {

        @Test
        void generar_ordenaAlfabeticamenteEscalaYTotaliza() {
            // Arrange
            when(alcance.sucursalOpcional(null)).thenReturn(Optional.empty());
            when(gananciasRepository.consultar(null, null, null)).thenReturn(List.of(
                    RepSucursalFixture.ganancia(RepSucursalFixture.QUETZALTENANGO, "Quetzaltenango", "1295", "1611"),
                    RepSucursalFixture.ganancia(RepSucursalFixture.GUATEMALA, "Ciudad de Guatemala", "85", "0"),
                    RepSucursalFixture.ganancia(RepSucursalFixture.HUEHUETENANGO, "Huehuetenango", "0", "0")));

            // Act
            ReporteTabla<FilaGanancia> reporte = ganancias.generar(todo, null);

            // Assert
            List<FilaGanancia> filas = reporte.grupos().getFirst().filas();
            assertAll(
                    () -> assertEquals(List.of("Ciudad de Guatemala", "Huehuetenango", "Quetzaltenango"),
                            filas.stream().map(FilaGanancia::sucursal).toList()),
                    () -> assertEquals(new BigDecimal("0.00"), filas.get(1).ingresos()),
                    () -> assertEquals(new BigDecimal("-316.00"), filas.get(2).ganancia()),
                    () -> assertEquals("1380.00", reporte.totales().get("ingresos")),
                    () -> assertEquals("1611.00", reporte.totales().get("gastos")),
                    () -> assertEquals("-231.00", reporte.totales().get("ganancia"))
            );
        }

        @Test
        void generar_conSucursal_laInforma() {
            // Arrange
            when(alcance.sucursalOpcional(RepSucursalFixture.QUETZALTENANGO))
                    .thenReturn(Optional.of(RepSucursalFixture.quetzaltenango()));
            when(gananciasRepository.consultar(null, null, RepSucursalFixture.QUETZALTENANGO)).thenReturn(List.of());

            // Act & Assert
            assertEquals("Quetzaltenango", ganancias.generar(todo, RepSucursalFixture.QUETZALTENANGO)
                    .filtros().otros().get("sucursal"));
        }
    }

    @Nested
    class RutasDemandadas {

        @Test
        void generar_conservaElRankingYSumaLosBoletos() {
            // Arrange
            FiltroFechas octubre = new FiltroFechas(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"));
            when(rutasRepository.consultar(octubre.desde(), octubre.hasta())).thenReturn(List.of(
                    RepRutaFixture.demandada("Quetzaltenango", "Ciudad de Guatemala", 7L, "595", false),
                    RepRutaFixture.demandada("Huehuetenango", null, 2L, "110", true),
                    RepRutaFixture.demandada("Quetzaltenango", "Huehuetenango", null, "0", null)));

            // Act
            ReporteTabla<FilaRutaDemandada> reporte = rutas.generar(octubre);

            // Assert
            List<FilaRutaDemandada> filas = reporte.grupos().getFirst().filas();
            assertAll(
                    () -> assertEquals(7L, filas.getFirst().boletosVendidos()),
                    () -> assertEquals(new BigDecimal("595.00"), filas.getFirst().ingreso()),
                    () -> assertEquals(new BigDecimal("55.00"), filas.getFirst().precioBoleto()),
                    () -> assertTrue(filas.get(1).eliminada()),
                    () -> assertEquals("—", filas.get(1).destino()),
                    () -> assertEquals(0L, filas.get(2).boletosVendidos()),
                    () -> assertEquals(0L, filas.get(2).viajesConVentas()),
                    () -> assertFalse(filas.get(2).eliminada()),
                    () -> assertEquals(9L, reporte.totales().get("boletosVendidos")),
                    () -> assertEquals("705.00", reporte.totales().get("ingreso")),
                    () -> assertEquals(LocalDate.parse("2026-10-01"), reporte.filtros().desde())
            );
        }
    }

    @Nested
    class TallerPorCategoria {

        @Test
        void generar_ordenaCategoriasDeMayorAMenorGastoConGranTotal() {
            // Arrange
            UUID idBus = UUID.randomUUID();
            when(alcance.sucursalOpcional(RepSucursalFixture.QUETZALTENANGO))
                    .thenReturn(Optional.of(RepSucursalFixture.quetzaltenango()));
            when(alcance.busDeSucursal(idBus, RepSucursalFixture.QUETZALTENANGO))
                    .thenReturn(Optional.of(RepBusFixture.bus(idBus, RepSucursalFixture.QUETZALTENANGO, "P900ABC")));
            when(tallerRepository.consultar(null, null, RepSucursalFixture.QUETZALTENANGO, idBus)).thenReturn(List.of(
                    RepGastoFixture.taller("Aceite", idBus, "P900ABC", "150.00"),
                    RepGastoFixture.taller("Aceite", idBus, "P900ABC", "450.00"),
                    RepGastoFixture.taller("Frenos", idBus, "P900ABC", "850.00"),
                    RepGastoFixture.taller("Frenos", idBus, "P900ABC", "300.00")));

            // Act
            ReporteTabla<FilaGastoTaller> reporte = tallerCategoria.generar(todo, RepSucursalFixture.QUETZALTENANGO,
                    idBus);

            // Assert
            assertAll(
                    () -> assertEquals(List.of("Frenos", "Aceite"),
                            reporte.grupos().stream().map(GrupoReporte::titulo).toList()),
                    () -> assertEquals("1150.00", reporte.grupos().getFirst().subtotales().get("monto")),
                    () -> assertEquals(2L, reporte.grupos().getFirst().subtotales().get("lineas")),
                    () -> assertEquals("1750.00", reporte.totales().get("monto")),
                    () -> assertEquals("P900ABC", reporte.filtros().otros().get("bus")),
                    () -> assertEquals("Quetzaltenango", reporte.filtros().otros().get("sucursal"))
            );
        }

        @Test
        void generar_sinFiltros_noInformaSucursalNiBus() {
            // Arrange
            when(alcance.sucursalOpcional(null)).thenReturn(Optional.empty());
            when(alcance.busDeSucursal(null, null)).thenReturn(Optional.empty());
            when(tallerRepository.consultar(null, null, null, null)).thenReturn(List.of());

            // Act & Assert
            assertTrue(tallerCategoria.generar(todo, null, null).filtros().otros().isEmpty());
        }
    }

    @Nested
    class Mapa {

        @Test
        void generar_conSucursal_devuelveRutasConCoordenadas() {
            // Arrange
            when(alcance.buscarSucursal(RepSucursalFixture.QUETZALTENANGO)).thenReturn(RepSucursalFixture.quetzaltenango());
            when(mapaRepository.consultar(RepSucursalFixture.QUETZALTENANGO)).thenReturn(List.of(
                    RepRutaFixture.mapa("Quetzaltenango", "Ciudad de Guatemala"), RepRutaFixture.mapa(null, null)));

            // Act
            ReporteTabla<FilaMapaRuta> reporte = mapa.generar(todo, RepSucursalFixture.QUETZALTENANGO);

            // Assert
            FilaMapaRuta ruta = reporte.grupos().getFirst().filas().getFirst();
            assertAll(
                    () -> assertEquals("Mapa de rutas desde Quetzaltenango", reporte.titulo()),
                    () -> assertEquals(new BigDecimal("14.834700"), ruta.latitudOrigen()),
                    () -> assertEquals(new BigDecimal("-90.513300"), ruta.longitudDestino()),
                    () -> assertEquals("—", reporte.grupos().getFirst().filas().get(1).origen()),
                    () -> assertEquals(2L, reporte.totales().get("rutas"))
            );
        }

        @Test
        void generar_sinSucursal_lanza400SinConsultar() {
            assertThrows(SucursalRequeridaException.class, () -> mapa.generar(todo, null));
            verifyNoInteractions(mapaRepository);
        }
    }
}
