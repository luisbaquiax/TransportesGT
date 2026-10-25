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
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaGasto;
import org.transportsgt.reportingservice.dto.response.reportsadmin.FilaIngreso;
import org.transportsgt.reportingservice.exception.SucursalNoEncontradaException;
import org.transportsgt.reportingservice.fixture.RepGastoFixture;
import org.transportsgt.reportingservice.fixture.RepIngresoFixture;
import org.transportsgt.reportingservice.fixture.RepSucursalFixture;
import org.transportsgt.reportingservice.repositories.reportsadmin.GastoConsulta;
import org.transportsgt.reportingservice.repositories.reportsadmin.ReporteGastosRepository;
import org.transportsgt.reportingservice.repositories.reportsadmin.ReporteIngresosRepository;
import org.transportsgt.reportingservice.services.comun.AlcanceSucursal;
import org.transportsgt.reportingservice.support.Soporte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReporteIngresosYGastosServiceImplTest {

    @Mock
    private ReporteIngresosRepository ingresosRepository;
    @Mock
    private ReporteGastosRepository gastosRepository;
    @Mock
    private AlcanceSucursal alcance;

    private ReporteIngresosServiceImpl ingresos;
    private ReporteGastosServiceImpl gastos;
    private FiltroFechas intervalo;
    private FiltroFechas todo;

    @BeforeEach
    void setUp() {
        ingresos = new ReporteIngresosServiceImpl(ingresosRepository, alcance, Soporte.armador(), Soporte.textos());
        gastos = new ReporteGastosServiceImpl(gastosRepository, alcance, Soporte.armador(), Soporte.textos());
        intervalo = new FiltroFechas(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"));
        todo = new FiltroFechas(null, null);
    }

    @Nested
    class GenerarIngresos {

        @Test
        void generar_agrupaPorSucursalEnOrdenAlfabeticoConSubtotalesYTotal() {
            // Arrange
            when(alcance.sucursalOpcional(null)).thenReturn(Optional.empty());
            when(ingresosRepository.consultar(intervalo.desde(), intervalo.hasta(), null)).thenReturn(List.of(
                    RepIngresoFixture.venta(RepSucursalFixture.QUETZALTENANGO, "Quetzaltenango", "170.00", 2),
                    RepIngresoFixture.alquiler(RepSucursalFixture.QUETZALTENANGO, "Quetzaltenango", "700.00"),
                    RepIngresoFixture.venta(RepSucursalFixture.GUATEMALA, "Ciudad de Guatemala", "85.00", 1),
                    RepIngresoFixture.venta(RepSucursalFixture.HUEHUETENANGO, null, "55.00", 1)));

            // Act
            ReporteTabla<FilaIngreso> reporte = ingresos.generar(intervalo, null);

            // Assert
            List<GrupoReporte<FilaIngreso>> grupos = reporte.grupos();
            FilaIngreso alquiler = grupos.get(1).filas().get(1);
            assertAll(
                    () -> assertEquals(ReporteIngresosServiceImpl.TITULO, reporte.titulo()),
                    () -> assertEquals(List.of("Ciudad de Guatemala", "Quetzaltenango", "Sucursal no sincronizada"),
                            grupos.stream().map(GrupoReporte::titulo).toList()),
                    () -> assertEquals("870.00", grupos.get(1).subtotales().get("monto")),
                    () -> assertEquals(2L, grupos.get(1).subtotales().get("boletos")),
                    () -> assertEquals("Quetzaltenango → Ciudad de Guatemala", grupos.get(1).filas().getFirst().descripcion()),
                    () -> assertEquals("Huehuetenango → Sololá", alquiler.descripcion()),
                    () -> assertEquals("1010.00", reporte.totales().get("monto")),
                    () -> assertEquals(4L, reporte.totales().get("ingresos")),
                    () -> assertEquals(4L, reporte.totales().get("boletos")),
                    () -> assertTrue(reporte.filtros().otros().isEmpty())
            );
        }

        @Test
        void generar_conSucursal_laInformaEnLosFiltros() {
            // Arrange
            when(alcance.sucursalOpcional(RepSucursalFixture.QUETZALTENANGO))
                    .thenReturn(Optional.of(RepSucursalFixture.quetzaltenango()));
            when(ingresosRepository.consultar(null, null, RepSucursalFixture.QUETZALTENANGO)).thenReturn(List.of());

            // Act
            ReporteTabla<FilaIngreso> reporte = ingresos.generar(todo, RepSucursalFixture.QUETZALTENANGO);

            // Assert
            assertAll(
                    () -> assertEquals("Quetzaltenango", reporte.filtros().otros().get("sucursal")),
                    () -> assertTrue(reporte.grupos().isEmpty()),
                    () -> assertEquals("0.00", reporte.totales().get("monto"))
            );
        }

        @Test
        void generar_conSucursalInexistente_propagaEl404() {
            // Arrange
            UUID id = UUID.randomUUID();
            when(alcance.sucursalOpcional(id)).thenThrow(new SucursalNoEncontradaException(id));

            // Act & Assert
            assertThrows(SucursalNoEncontradaException.class, () -> ingresos.generar(todo, id));
        }
    }

    @Nested
    class GenerarGastos {

        @Test
        void generar_separaTallerYRepuestoYDesglosaPorTipo() {
            // Arrange
            when(alcance.sucursalOpcional(null)).thenReturn(Optional.empty());
            when(gastosRepository.consultar(null, null, null)).thenReturn(List.of(
                    RepGastoFixture.gasto(RepSucursalFixture.QUETZALTENANGO, "Quetzaltenango", "TALLER", "REPUESTO", "850.00"),
                    RepGastoFixture.gasto(RepSucursalFixture.QUETZALTENANGO, "Quetzaltenango", "TALLER", "MANO_DE_OBRA", "300.00"),
                    RepGastoFixture.gasto(RepSucursalFixture.HUEHUETENANGO, "Huehuetenango", "COMBUSTIBLE", null, "420.00"),
                    RepGastoFixture.gasto(RepSucursalFixture.HUEHUETENANGO, "Huehuetenango", "SALARIO_CHOFER", null, "172.50"),
                    RepGastoFixture.gasto(RepSucursalFixture.HUEHUETENANGO, "Huehuetenango", "DEPRECIACION", null, "411.00")));

            // Act
            ReporteTabla<FilaGasto> reporte = gastos.generar(todo, null);

            // Assert
            GrupoReporte<FilaGasto> huehue = reporte.grupos().getFirst();
            GrupoReporte<FilaGasto> xela = reporte.grupos().get(1);
            assertAll(
                    () -> assertEquals("Huehuetenango", huehue.titulo()),
                    () -> assertEquals("1003.50", huehue.subtotales().get("monto")),
                    () -> assertEquals("0.00", huehue.subtotales().get("TALLER")),
                    () -> assertEquals("REPUESTO", xela.filas().getFirst().tipo()),
                    () -> assertEquals("TALLER", xela.filas().get(1).tipo()),
                    () -> assertEquals("850.00", xela.subtotales().get("REPUESTO")),
                    () -> assertEquals("300.00", xela.subtotales().get("TALLER")),
                    () -> assertEquals("2153.50", reporte.totales().get("monto")),
                    () -> assertEquals("420.00", reporte.totales().get("COMBUSTIBLE")),
                    () -> assertEquals(5L, reporte.totales().get("gastos"))
            );
        }

        @Test
        void descripcion_segunElTipoDeGasto() {
            // Arrange
            GastoConsulta taller = RepGastoFixture.gasto(null, null, "TALLER", "REPUESTO", "1");
            GastoConsulta tallerSinDetalle = new RepGastoFixture.Gasto(null, null, null, "TALLER", null, null, null,
                    null, null, null, null, null, BigDecimal.ONE);
            GastoConsulta salario = RepGastoFixture.gasto(null, null, "SALARIO_CHOFER", null, "1");
            GastoConsulta salarioSinChofer = new RepGastoFixture.Gasto(null, null, null, "SALARIO_CHOFER", null, null,
                    null, null, null, null, null, null, BigDecimal.ONE);
            GastoConsulta depreciacion = RepGastoFixture.gasto(null, null, "DEPRECIACION", null, "1");
            GastoConsulta depreciacionSinKm = new RepGastoFixture.Gasto(null, null, null, "DEPRECIACION", null, null,
                    null, null, null, null, null, null, BigDecimal.ONE);
            GastoConsulta combustible = RepGastoFixture.gasto(null, null, "COMBUSTIBLE", null, "1");

            // Act & Assert
            assertAll(
                    () -> assertEquals("Frenos: Pastillas", gastos.descripcion(taller)),
                    () -> assertEquals("—", gastos.descripcion(tallerSinDetalle)),
                    () -> assertEquals("Salario de José Méndez", gastos.descripcion(salario)),
                    () -> assertEquals("Salario del chofer", gastos.descripcion(salarioSinChofer)),
                    () -> assertEquals("Depreciación: 205.5 km × Q 2 por km", gastos.descripcion(depreciacion)),
                    () -> assertEquals("Depreciación: — km × Q — por km", gastos.descripcion(depreciacionSinKm)),
                    () -> assertEquals("Combustible del viaje", gastos.descripcion(combustible))
            );
        }

        @Test
        void generar_conSucursal_laInformaEnLosFiltros() {
            // Arrange
            when(alcance.sucursalOpcional(RepSucursalFixture.QUETZALTENANGO))
                    .thenReturn(Optional.of(RepSucursalFixture.quetzaltenango()));
            when(gastosRepository.consultar(intervalo.desde(), intervalo.hasta(), RepSucursalFixture.QUETZALTENANGO))
                    .thenReturn(List.of());

            // Act & Assert
            assertEquals("Quetzaltenango", gastos.generar(intervalo, RepSucursalFixture.QUETZALTENANGO)
                    .filtros().otros().get("sucursal"));
        }
    }
}
