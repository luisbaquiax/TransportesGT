package org.transportsgt.reportingservice.services.exportacion.jasper;

import net.sf.jasperreports.engine.JasperCompileManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.transportsgt.reportingservice.dto.response.FiltrosAplicados;
import org.transportsgt.reportingservice.dto.response.GrupoReporte;
import org.transportsgt.reportingservice.dto.response.ReporteTabla;
import org.transportsgt.reportingservice.dto.response.reportsadmin.*;
import org.transportsgt.reportingservice.dto.response.reportssucursal.*;
import org.transportsgt.reportingservice.services.exportacion.impl.GeneradorReportesJasper;
import org.transportsgt.reportingservice.services.exportacion.reports.ReporteJasper;
import org.transportsgt.reportingservice.services.exportacion.reports.ConversorFilasTallerJasper;
import org.transportsgt.reportingservice.services.exportacion.reports.FormateadorReporteJasper;
import org.transportsgt.reportingservice.services.exportacion.reports.SoporteAdaptadoresJasper;
import org.transportsgt.reportingservice.services.exportacion.reports.reportsadmin.*;
import org.transportsgt.reportingservice.services.exportacion.reports.reportssucursal.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class PlantillasJasperTest {
    private final CatalogoPlantillasJasper catalogo = new CatalogoPlantillasJasper();

    @Test
    void plantillasJasper_compilanLasDoceSinErrores() {
        catalogo.todas().forEach((nombre, plantilla) ->
                Assertions.assertDoesNotThrow(() -> JasperCompileManager.compileReport(
                        new ClassPathResource(plantilla.ruta()).getInputStream()), nombre));
    }

    @Test
    void generar_conListasDeConsultasExistentes_generaLosDocePdf() {
        GeneradorReportesJasper generador = new GeneradorReportesJasper(
                adaptadores(), catalogo, new ParametrosReporteJasper());
        catalogo.todas().keySet().forEach(nombre -> {
            byte[] pdf = generador.generar(nombre, reporte(nombre));
            Assertions.assertAll(nombre,
                    () -> Assertions.assertTrue(pdf.length > 1000),
                    () -> Assertions.assertTrue(new String(pdf, 0, 4,
                            java.nio.charset.StandardCharsets.US_ASCII).startsWith("%PDF")));
        });
    }

    private List<ReporteJasper> adaptadores() {
        SoporteAdaptadoresJasper soporte = new SoporteAdaptadoresJasper();
        FormateadorReporteJasper formato = new FormateadorReporteJasper();
        ConversorFilasTallerJasper taller = new ConversorFilasTallerJasper(soporte, formato);
        return List.of(new AdaptadorIngresosJasper(soporte, formato), new AdaptadorGastosJasper(soporte, formato),
                new AdaptadorGananciasJasper(soporte, formato), new AdaptadorRutasDemandadasJasper(soporte, formato),
                new AdaptadorGastosTallerCategoriaJasper(taller), new AdaptadorMapaRutasJasper(soporte, formato),
                new AdaptadorBusesJasper(soporte, formato), new AdaptadorChoferesJasper(soporte, formato),
                new AdaptadorIngresosBoletosJasper(soporte, formato),
                new AdaptadorIngresosAlquileresJasper(soporte, formato),
                new AdaptadorDepreciacionBusesJasper(soporte, formato), new AdaptadorGastosTallerBusJasper(taller));
    }

    private ReporteTabla<?> reporte(String nombre) {
        UUID sucursal = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        BigDecimal cien = new BigDecimal("100.00");
        Object fila = switch (nombre) {
            case "ingresos" -> new FilaIngreso(sucursal, "Guatemala", java.time.LocalDate.now(),
                    "VENTA_BOLETOS", id, "Guatemala → Antigua", "José Pérez", "1234567-K", 2, "EN_LINEA", cien);
            case "gastos" -> new FilaGasto(sucursal, "Guatemala", java.time.LocalDate.now(),
                    "COMBUSTIBLE", id, "P001ABC", "Combustible", cien);
            case "ganancias" -> new FilaGanancia(sucursal, "Guatemala", cien, new BigDecimal("40.00"),
                    new BigDecimal("60.00"));
            case "rutas-demandadas" -> new FilaRutaDemandada(id, "Guatemala", "Antigua",
                    new BigDecimal("45"), new BigDecimal("35"), 12, 2, cien, false);
            case "gastos-taller-por-categoria", "gastos-taller-por-bus" -> new FilaGastoTaller(
                    "Motor", id, "P001ABC", "Guatemala", java.time.LocalDate.now(), "Cambio", "REPUESTO", cien);
            case "mapa-rutas" -> new FilaMapaRuta(id, "Guatemala", new BigDecimal("14.63"),
                    new BigDecimal("-90.51"), "Antigua", new BigDecimal("14.55"), new BigDecimal("-90.73"),
                    new BigDecimal("45"), new BigDecimal("35"));
            case "buses" -> new FilaBus(id, "P001ABC", "Mercedes", "2026", 50, "DISPONIBLE",
                    "José Pérez", new BigDecimal("25000"), 14, true);
            case "choferes" -> new FilaChofer(id, "A-123", "José Pérez", "A",
                    java.time.LocalDate.now().plusYears(1), "VIGENTE", true, 14);
            case "ingresos-boletos" -> new FilaIngresoBoleto(id, "Guatemala → Antigua",
                    OffsetDateTime.now(), "P001ABC", 2, cien);
            case "ingresos-alquileres" -> new FilaIngresoAlquiler(id, "José Pérez", "1234567-K",
                    "Guatemala", "Antigua", OffsetDateTime.now(), OffsetDateTime.now().plusDays(1),
                    "P001ABC", java.time.LocalDate.now(), cien);
            case "depreciacion-buses" -> new FilaDepreciacionBus(id, "P001ABC", new BigDecimal("45"),
                    new BigDecimal("2.00"), cien, new BigDecimal("2.22"));
            default -> throw new IllegalArgumentException(nombre);
        };
        return tabla(sucursal, fila);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ReporteTabla<?> tabla(UUID sucursal, Object fila) {
        GrupoReporte grupo = new GrupoReporte(sucursal.toString(), "Guatemala", List.of(fila), null,
                Map.of("monto", "100.00"));
        return new ReporteTabla<>("Reporte de prueba con tildes: José", new FiltrosAplicados(null, null,
                Map.of("sucursal", "Guatemala")), OffsetDateTime.now(), List.of(grupo), Map.of("monto", "100.00"));
    }
}
