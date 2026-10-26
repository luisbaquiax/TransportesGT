package org.transportsgt.reportingservice.services.comun;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.transportsgt.reportingservice.dto.request.FiltroFechas;
import org.transportsgt.reportingservice.dto.response.FiltrosAplicados;
import org.transportsgt.reportingservice.dto.response.GrupoReporte;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.support.Soporte;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ArmadorReporteTablaTest {

    record Fila(String sucursal, BigDecimal monto, long boletos) {
    }

    private ArmadorReporteTabla armador;
    private List<Fila> filas;

    @BeforeEach
    void setUp() {
        armador = Soporte.armador();
        filas = List.of(
                new Fila("Quetzaltenango", new BigDecimal("100.005"), 2),
                new Fila("Ciudad de Guatemala", new BigDecimal("50"), 1),
                new Fila("Quetzaltenango", null, 0),
                new Fila("Álamo", new BigDecimal("10.10"), 3));
    }

    private Map<String, Object> subtotal(List<Fila> grupo) {
        return armador.totales().dinero("monto", armador.sumar(grupo, Fila::monto))
                .cantidad("boletos", armador.contar(grupo, Fila::boletos)).mapa();
    }

    @Nested
    class Agrupar {

        @Test
        void agrupar_conservaElOrdenDeLlegadaYCalculaSubtotales() {
            // Act
            List<GrupoReporte<Fila>> grupos = armador.agrupar(filas, Fila::sucursal, Fila::sucursal, ArmadorReporteTablaTest.this::subtotal);

            // Assert
            assertAll(
                    () -> assertEquals(List.of("Quetzaltenango", "Ciudad de Guatemala", "Álamo"),
                            grupos.stream().map(GrupoReporte::titulo).toList()),
                    () -> assertEquals(2, grupos.getFirst().filas().size()),
                    () -> assertEquals("100.01", grupos.getFirst().subtotales().get("monto")),
                    () -> assertEquals(2L, grupos.getFirst().subtotales().get("boletos"))
            );
        }

        @Test
        void ordenarPorTitulo_usaOrdenAlfabeticoEspanolSinImportarTildes() {
            // Arrange
            List<GrupoReporte<Fila>> grupos = armador.agrupar(filas, Fila::sucursal, Fila::sucursal, ArmadorReporteTablaTest.this::subtotal);

            // Act
            List<GrupoReporte<Fila>> ordenados = armador.ordenarPorTitulo(grupos);

            // Assert
            assertEquals(List.of("Álamo", "Ciudad de Guatemala", "Quetzaltenango"),
                    ordenados.stream().map(GrupoReporte::titulo).toList());
        }

        @Test
        void ordenarPorTitulo_tituloNuloVaPrimero() {
            // Arrange
            List<GrupoReporte<Fila>> grupos = List.of(new GrupoReporte<>("b", "Beta", List.of(), null, null),
                    new GrupoReporte<>("x", null, List.of(), null, null));

            // Act & Assert
            assertNull(armador.ordenarPorTitulo(grupos).getFirst().titulo());
        }

        @Test
        void ordenarPorMontoDesc_dejaPrimeroElDeMayorGasto() {
            // Arrange
            List<GrupoReporte<Fila>> grupos = armador.agrupar(filas, Fila::sucursal, Fila::sucursal, ArmadorReporteTablaTest.this::subtotal);

            // Act
            List<GrupoReporte<Fila>> ordenados = armador.ordenarPorMontoDesc(grupos,
                    g -> armador.sumar(g.filas(), Fila::monto));

            // Assert
            assertEquals(List.of("Quetzaltenango", "Ciudad de Guatemala", "Álamo"),
                    ordenados.stream().map(GrupoReporte::titulo).toList());
        }

        @Test
        void sinAgruparYGrupoConSubgrupos_armanLaEstructura() {
            // Act
            List<GrupoReporte<Fila>> unico = armador.sinAgrupar(filas);
            GrupoReporte<Fila> padre = armador.grupoConSubgrupos("bus", "P900ABC", unico, Map.of("monto", "1.00"));

            // Assert
            assertAll(
                    () -> assertNull(unico.getFirst().clave()),
                    () -> assertEquals(4, unico.getFirst().filas().size()),
                    () -> assertNull(padre.filas()),
                    () -> assertEquals(1, padre.subgrupos().size())
            );
        }
    }

    @Nested
    class Tabla {

        @Test
        void tabla_llevaTituloFiltrosTotalesYFechaDeGuatemala() {
            // Arrange
            FiltrosAplicados filtros = armador.filtros(new FiltroFechas(LocalDate.parse("2026-10-01"), null),
                    Map.of("sucursal", "Quetzaltenango"));

            // Act
            ReporteTabla<Fila> tabla = armador.tabla("Reporte", filtros, armador.sinAgrupar(filas), subtotal(filas));

            // Assert
            assertAll(
                    () -> assertEquals("Reporte", tabla.titulo()),
                    () -> assertEquals(LocalDate.parse("2026-10-01"), tabla.filtros().desde()),
                    () -> assertNull(tabla.filtros().hasta()),
                    () -> assertEquals("Quetzaltenango", tabla.filtros().otros().get("sucursal")),
                    () -> assertEquals("160.11", tabla.totales().get("monto")),
                    () -> assertEquals(6L, tabla.totales().get("boletos")),
                    () -> assertEquals("2026-10-24T12:00-06:00", tabla.generadoEn().toString())
            );
        }

        @Test
        void totales_decimalQuitaCerosYNuloEsCero() {
            Map<String, Object> mapa = armador.totales().decimal("km", new BigDecimal("205.50"))
                    .decimal("nulo", null).mapa();
            assertAll(
                    () -> assertEquals("205.5", mapa.get("km")),
                    () -> assertEquals("0", mapa.get("nulo"))
            );
        }
    }
}
